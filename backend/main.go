package main

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"regexp"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	veyraDomain        = "@veyra.com"
	passwordIterations = 210000
	passwordKeyLength  = 32
	passwordSaltLength = 16
	sessionLifetime    = 30 * 24 * time.Hour
)

var (
	usernameRe = regexp.MustCompile(`^[A-Za-z0-9_]{3,32}$`)
	emailRe    = regexp.MustCompile(`^[a-z0-9][a-z0-9._-]{2,31}@vayra\.com$`)
)

type server struct {
	db       *pgxpool.Pool
	mu       sync.Mutex
	attempts map[string][]time.Time
}

type authResponse struct {
	OK       bool   `json:"ok"`
	Error    string `json:"error,omitempty"`
	Token    string `json:"token,omitempty"`
	Username string `json:"username,omitempty"`
	Email    string `json:"email,omitempty"`
}

type registerRequest struct {
	Username string `json:"username"`
	Email    string `json:"email"`
	Password string `json:"password"`
}
type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	dsn := strings.TrimSpace(os.Getenv("DATABASE_URL"))
	if dsn == "" {
		log.Fatal("DATABASE_URL is required")
	}

	db, err := pgxpool.New(ctx, dsn)
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()

	pingCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if err := db.Ping(pingCtx); err != nil {
		log.Fatal("database connection failed: ", err)
	}
	if err := migrate(ctx, db); err != nil {
		log.Fatal("database migration failed: ", err)
	}

	s := &server{db: db, attempts: make(map[string][]time.Time)}
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.health)
	mux.HandleFunc("/v1/auth/register", s.register)
	mux.HandleFunc("/v1/auth/login", s.login)
	mux.HandleFunc("/v1/auth/me", s.me)
	mux.HandleFunc("/v1/auth/logout", s.logout)

	addr := os.Getenv("LISTEN_ADDR")
	if addr == "" {
		addr = "127.0.0.1:8080"
	}
	readTimeout := envDuration("HTTP_READ_TIMEOUT", 10*time.Second)
	writeTimeout := envDuration("HTTP_WRITE_TIMEOUT", 15*time.Second)
	idleTimeout := envDuration("HTTP_IDLE_TIMEOUT", 60*time.Second)
	srv := &http.Server{Addr: addr, Handler: securityHeaders(mux), ReadTimeout: readTimeout, WriteTimeout: writeTimeout, IdleTimeout: idleTimeout}

	go func() {
		log.Printf("Veyra API listening on %s", addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	}()
	<-ctx.Done()

	shutdownCtx, cancelShutdown := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancelShutdown()
	_ = srv.Shutdown(shutdownCtx)
	log.Println("Veyra API stopped")
}

func migrate(ctx context.Context, db *pgxpool.Pool) error {
	_, err := db.Exec(ctx, `
        create table if not exists users (
            id bigserial primary key,
            username text not null unique,
            email text not null unique,
            password_hash text not null,
            created_at timestamptz not null default now(),
            last_login_at timestamptz
        );
        create table if not exists sessions (
            id bigserial primary key,
            token_hash text not null unique,
            user_id bigint not null references users(id) on delete cascade,
            created_at timestamptz not null default now(),
            expires_at timestamptz not null
        );
        create index if not exists sessions_user_idx on sessions(user_id);
        create index if not exists sessions_expiry_idx on sessions(expires_at);
    `)
	return err
}

func (s *server) health(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, 405, authResponse{Error: "method not allowed"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()
	if err := s.db.Ping(ctx); err != nil {
		writeJSON(w, 503, map[string]any{"ok": false, "service": "veyra-api"})
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true, "service": "veyra-api"})
}

func (s *server) register(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, 405, authResponse{Error: "method not allowed"})
		return
	}
	var req registerRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	req.Username = strings.TrimSpace(req.Username)
	req.Email = strings.ToLower(strings.TrimSpace(req.Email))
	if !usernameRe.MatchString(req.Username) {
		writeJSON(w, 400, authResponse{Error: "invalid Vayra ID"})
		return
	}
	if !emailRe.MatchString(req.Email) || !strings.HasSuffix(req.Email, veyraDomain) {
		writeJSON(w, 400, authResponse{Error: "Vayra email must use @veyra.com"})
		return
	}
	if len(req.Password) < 10 || len(req.Password) > 128 {
		writeJSON(w, 400, authResponse{Error: "password must be 10-128 characters"})
		return
	}
	if !s.allow("register:" + req.Email) {
		writeJSON(w, 429, authResponse{Error: "too many attempts; try again later"})
		return
	}

	passwordHash, err := hashPassword(req.Password)
	if err != nil {
		writeJSON(w, 500, authResponse{Error: "could not secure password"})
		return
	}
	var userID int64
	err = s.db.QueryRow(r.Context(), `insert into users(username,email,password_hash) values($1,$2,$3) returning id`, req.Username, req.Email, passwordHash).Scan(&userID)
	if err != nil {
		if strings.Contains(strings.ToLower(err.Error()), "duplicate") || strings.Contains(strings.ToLower(err.Error()), "unique") {
			writeJSON(w, 409, authResponse{Error: "Vayra ID or email is already in use"})
			return
		}
		writeJSON(w, 500, authResponse{Error: "could not create account"})
		return
	}
	token, err := s.createSession(r.Context(), userID)
	if err != nil {
		writeJSON(w, 500, authResponse{Error: "could not create session"})
		return
	}
	writeJSON(w, 201, authResponse{OK: true, Token: token, Username: req.Username, Email: req.Email})
}

func (s *server) login(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, 405, authResponse{Error: "method not allowed"})
		return
	}
	var req loginRequest
	if !decodeJSON(w, r, &req) {
		return
	}
	req.Email = strings.ToLower(strings.TrimSpace(req.Email))
	if !emailRe.MatchString(req.Email) || req.Password == "" {
		writeJSON(w, 400, authResponse{Error: "invalid Vayra email or password"})
		return
	}
	if !s.allow("login:" + req.Email) {
		writeJSON(w, 429, authResponse{Error: "too many login attempts; try again later"})
		return
	}

	var userID int64
	var username, storedHash string
	err := s.db.QueryRow(r.Context(), `select id,username,password_hash from users where email=$1`, req.Email).Scan(&userID, &username, &storedHash)
	if err != nil && err != pgx.ErrNoRows {
		writeJSON(w, 500, authResponse{Error: "database error"})
		return
	}
	valid := false
	if err == nil {
		valid, err = verifyPassword(req.Password, storedHash)
		if err != nil {
			writeJSON(w, 500, authResponse{Error: "password verification failed"})
			return
		}
	}
	if !valid {
		writeJSON(w, 401, authResponse{Error: "invalid Vayra email or password"})
		return
	}
	if _, err := s.db.Exec(r.Context(), `update users set last_login_at=now() where id=$1`, userID); err != nil {
		writeJSON(w, 500, authResponse{Error: "could not update login"})
		return
	}
	token, err := s.createSession(r.Context(), userID)
	if err != nil {
		writeJSON(w, 500, authResponse{Error: "could not create session"})
		return
	}
	writeJSON(w, 200, authResponse{OK: true, Token: token, Username: username, Email: req.Email})
}

func (s *server) me(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, 405, authResponse{Error: "method not allowed"})
		return
	}
	userID, err := s.authenticatedUser(r.Context(), r)
	if err != nil {
		writeJSON(w, 401, authResponse{Error: "unauthorized"})
		return
	}
	var username, email string
	if err := s.db.QueryRow(r.Context(), `select username,email from users where id=$1`, userID).Scan(&username, &email); err != nil {
		writeJSON(w, 401, authResponse{Error: "unauthorized"})
		return
	}
	writeJSON(w, 200, authResponse{OK: true, Username: username, Email: email})
}

func (s *server) logout(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, 405, authResponse{Error: "method not allowed"})
		return
	}
	if token := bearerToken(r); token != "" {
		_, _ = s.db.Exec(r.Context(), `delete from sessions where token_hash=$1`, tokenHash(token))
	}
	writeJSON(w, 200, authResponse{OK: true})
}

func (s *server) createSession(ctx context.Context, userID int64) (string, error) {
	token := randomToken()
	_, err := s.db.Exec(ctx, `insert into sessions(token_hash,user_id,expires_at) values($1,$2,now()+$3::interval)`, tokenHash(token), userID, sessionLifetime.String())
	return token, err
}
func (s *server) authenticatedUser(ctx context.Context, r *http.Request) (int64, error) {
	token := bearerToken(r)
	if token == "" {
		return 0, errors.New("missing token")
	}
	var userID int64
	err := s.db.QueryRow(ctx, `select user_id from sessions where token_hash=$1 and expires_at>now()`, tokenHash(token)).Scan(&userID)
	return userID, err
}
func (s *server) allow(key string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	cutoff := now.Add(-10 * time.Minute)
	fresh := s.attempts[key][:0]
	for _, t := range s.attempts[key] {
		if t.After(cutoff) {
			fresh = append(fresh, t)
		}
	}
	if len(fresh) >= 5 {
		s.attempts[key] = fresh
		return false
	}
	s.attempts[key] = append(fresh, now)
	return true
}

func hashPassword(password string) (string, error) {
	salt := make([]byte, passwordSaltLength)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	key := pbkdf2SHA256([]byte(password), salt, passwordIterations, passwordKeyLength)
	return fmt.Sprintf("pbkdf2-sha256$%d$%s$%s", passwordIterations, base64.RawStdEncoding.EncodeToString(salt), base64.RawStdEncoding.EncodeToString(key)), nil
}
func verifyPassword(password, encoded string) (bool, error) {
	parts := strings.Split(encoded, "$")
	if len(parts) != 4 || parts[0] != "pbkdf2-sha256" {
		return false, errors.New("unsupported password hash")
	}
	var iterations int
	if _, err := fmt.Sscanf(parts[1], "%d", &iterations); err != nil || iterations < 100000 || iterations > 1000000 {
		return false, errors.New("invalid password hash")
	}
	salt, err := base64.RawStdEncoding.DecodeString(parts[2])
	if err != nil {
		return false, err
	}
	expected, err := base64.RawStdEncoding.DecodeString(parts[3])
	if err != nil {
		return false, err
	}
	actual := pbkdf2SHA256([]byte(password), salt, iterations, len(expected))
	return subtle.ConstantTimeCompare(actual, expected) == 1, nil
}
func pbkdf2SHA256(password, salt []byte, iterations, keyLen int) []byte {
	out := make([]byte, 0, keyLen)
	for block := uint32(1); len(out) < keyLen; block++ {
		mac := hmac.New(sha256.New, password)
		mac.Write(salt)
		mac.Write([]byte{byte(block >> 24), byte(block >> 16), byte(block >> 8), byte(block)})
		u := mac.Sum(nil)
		t := append([]byte(nil), u...)
		for i := 1; i < iterations; i++ {
			mac = hmac.New(sha256.New, password)
			mac.Write(u)
			u = mac.Sum(nil)
			for j := range t {
				t[j] ^= u[j]
			}
		}
		out = append(out, t...)
	}
	return out[:keyLen]
}
func randomToken() string {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b[:])
}
func tokenHash(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
func bearerToken(r *http.Request) string {
	v := strings.TrimSpace(r.Header.Get("Authorization"))
	if len(v) < 8 || !strings.EqualFold(v[:7], "Bearer ") {
		return ""
	}
	return strings.TrimSpace(v[7:])
}
func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 32<<10)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		writeJSON(w, 400, authResponse{Error: "invalid request"})
		return false
	}
	return true
}
func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func envDuration(name string, fallback time.Duration) time.Duration {
	if v := strings.TrimSpace(os.Getenv(name)); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return fallback
}

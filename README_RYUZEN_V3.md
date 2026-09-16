VeyraChat v3 — Telegram-style UX

Added:
- More polished Material 3 visual hierarchy
- Functional Privacy & Security screen with local UI state toggles
- Terms of Use shown during registration; registration requires acceptance
- In-app Back navigation
- Android back: one press navigates out of chat/settings; at root a first press shows an exit message and a second press within 2 seconds exits the app
- Chat list, search, drawer, profile/security/settings foundations
- Demo chat state and local message sending

Build:
gradlew.bat :app:compileDebugKotlin --console=plain
gradlew.bat assembleDebug

Note: authentication, 2FA, biometrics, sessions, media upload, realtime messaging and server-side privacy are UI/local demo behavior until Go backend is connected.

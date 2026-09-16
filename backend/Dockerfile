FROM golang:1.27-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/vayra-api .

FROM alpine:3.22
RUN addgroup -S vayra && adduser -S -G vayra vayra
COPY --from=build /out/vayra-api /usr/local/bin/vayra-api
USER vayra
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/vayra-api"]

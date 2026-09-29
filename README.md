# User Portal: Spring Boot + Angular login

The user signs in and sees their profile details. The backend is a stateless Spring Boot REST API.
The frontend is an Angular Material SPA.

| Part | Stack |
|---|---|
| `Backend/` | Spring Boot 3.5, Java 17, Spring Security (OAuth2 resource server / Nimbus JWT), Spring Data JPA, Flyway, H2 (dev), PostgreSQL (prod) |
| `Frontend/` | Angular 22 (standalone, signals, zoneless), Angular Material 3, Vitest, ESLint |

## How authentication works

```
Browser ──(same origin)──► Angular dev server / reverse proxy ──/api──► Spring Boot
```

1. `POST /api/auth/login` checks the credentials with BCrypt. If they're correct, it returns
   **204** and sets the JWT as a cookie: `access_token; HttpOnly; SameSite=Strict; Path=/api`.
   The token is never in the response body, and JavaScript can't read it, so an XSS bug can't steal it.
2. The browser sends that cookie on every `/api` call. Spring Security reads it with
   `CookieBearerTokenResolver` and checks the signature (HS256), expiry and issuer.
3. **CSRF**: cookie-based auth needs CSRF protection. Spring sets a readable `XSRF-TOKEN` cookie,
   and Angular's `HttpClient` echoes it back in the `X-XSRF-TOKEN` header (double-submit cookie).
4. "Logged in" in the SPA means `GET /api/users/me` succeeded. The app calls it once at startup,
   so a reload keeps the session, and keeps the user in a signal. Guards read that signal.
5. When a session expires, the next API call gets a 401. The interceptor clears the session and
   redirects to `/login?reason=expired`, which shows a message. The profile page refreshes when
   it opens and when the tab becomes visible again, so an expired session is noticed then.

All errors come back as RFC 9457 `ProblemDetail` JSON from one `@RestControllerAdvice`, plus a
`timestamp` and the `requestId`. Validation and duplicate errors also include an `errors` map
(field → message), and the UI shows each message under its field.

## Cross-cutting concerns

- **Logging (AOP).** `LoggingAspect` wraps every `@RestController` and `@Service` method.
  - DEBUG: entry with its arguments. INFO: controller timings. WARN: calls over 500 ms.
  - Return values are never logged. Passwords are masked, tokens are logged by type only, and
    newlines are stripped so values can't forge log lines.
  - `RequestIdFilter` tags each request with an `X-Request-Id`, which appears on every log line
    (`[requestId]`), in the response header, and in error bodies.
- **Error handling.** `GlobalExceptionHandler` (`@RestControllerAdvice`) maps each exception to a
  status code and picks the log level. 4xx client mistakes go to DEBUG/INFO, security events (403,
  429) to WARN, and only real 5xx faults to ERROR with a stack trace. The client never sees internals.
- **Rate limiting (Bucket4j).** Token buckets per client IP, checked before Spring Security, so
  rejected requests cost no BCrypt work.
  - Login and register share a strict budget (10/min in prod, 30/min in dev); the rest of `/api` gets 120/min.
  - Over the limit: `429` with `Retry-After`. Every allowed response carries `X-RateLimit-Remaining`.
  - The buckets live in a size-bounded, expiring cache.
  - Limits are per instance. For several replicas, switch to Bucket4j's Redis backend.
  - Behind a proxy, set `FORWARD_HEADERS_STRATEGY=native` so the real client IP is used.
  - `X-Forwarded-For` is never trusted on its own.

## Quick start

```bash
./run.sh          # start backend (:8080) + frontend (:4200); Ctrl+C stops both
./run.sh test     # all automated checks
./run.sh smoke    # live API checks with curl, including the secured endpoints
```

Reviewers: start with **[REVIEWER_GUIDE.md](REVIEWER_GUIDE.md)**.

## Prerequisites

- JDK 17+
- Node **22.22.3+** (or 24.15+ / 26+), which is what the Angular 22 CLI enforces. Node 21 and older 22.x fail on `npm start`. With nvm, run `nvm use 22.22.3`.
- Docker (only for the PostgreSQL `prod` profile)

Maven isn't needed: use the wrapper (`mvnw` / `mvnw.cmd`).

## Run in development

```bash
# 1. Backend on :8080 (H2 in memory, seeded user, Swagger UI)
cd Backend
./mvnw spring-boot:run

# 2. Frontend on :4200 (proxies /api to :8080)
cd Frontend
npm ci
npm start
```

Open http://localhost:4200.

**Test logins (dev profile only):** `demo` / `Demo12345` (USER) and `admin` / `Admin12345` (ADMIN).
They're set in `Backend/src/main/resources/application-dev.yml` under `app.seed.users`. You can also
register a new account; new accounts always get the USER role.

Dev extras: Swagger UI at http://localhost:8080/swagger-ui.html and the H2 console at
http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:userauth`, user `sa`, empty password).

If port 8080 is taken:

```bash
SERVER_PORT=8090 ./mvnw spring-boot:run            # backend
API_TARGET=http://localhost:8090 npm start          # frontend proxy
```

## Run with the `prod` profile (PostgreSQL)

```bash
docker compose up -d
cd Backend
./mvnw -DskipTests package
JWT_SECRET="$(openssl rand -base64 48)" \
DB_URL=jdbc:postgresql://localhost:5432/userauth DB_USER=userauth DB_PASSWORD=userauth-local \
java -jar target/user-auth-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

In prod there's no seeded user, no H2 console and no Swagger. The cookie is `Secure`, so serve
the app over HTTPS behind a reverse proxy that routes `/api` to the backend and everything else
to the built SPA (`Frontend/dist/user-auth-ui/browser`). Startup fails if `JWT_SECRET` is missing
or shorter than 32 characters.

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | none (dev has a dev-only default) | HS256 signing key, at least 32 characters |
| `JWT_TTL` | `30m` | Token and cookie lifetime (`15m`, `1h`, and so on) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | none | PostgreSQL connection (prod) |
| `CORS_ALLOWED_ORIGINS` | empty | Only needed if the SPA is served from a different origin than the API |
| `SERVER_PORT` | `8080` | Backend port |
| `RATE_LIMIT_AUTH_CAPACITY` | `10` (dev `30`) | Login + register requests per minute per IP |
| `RATE_LIMIT_API_CAPACITY` | `120` | Other `/api` requests per minute per IP |
| `FORWARD_HEADERS_STRATEGY` | `none` | Set `native` behind a trusted reverse proxy (real client IP for rate limiting) |

## API

| Method | Path | Auth | Result |
|---|---|---|---|
| POST | `/api/auth/login` | public + CSRF | 204 and sets the auth cookie · 400 validation · 401 `Invalid username or password.` |
| POST | `/api/auth/register` | public + CSRF | 201 `UserResponse` · 400 field errors · 409 username/email taken |
| POST | `/api/auth/logout` | public + CSRF | 204 and clears the cookie |
| GET | `/api/users/me` | cookie | 200 `{id, username, email, fullName, role}` · 401 |
| GET | `/api/admin/users?page=0&size=20` | cookie + **ADMIN** | 200 `{content, page, size, totalElements, totalPages}` · 401 · 403 · 400 if size > 100 |
| GET | `/actuator/health` | public | 200 `{"status":"UP"}` |

Registration rules (the same on client and server):
- **username:** 3–50 characters from `A-Z a-z 0-9 . _ -`
- **email:** a valid address
- **password:** 8–72 characters, with at least one letter and one digit

Usernames and emails are stored lowercase, so they are unique regardless of case.

## Tests

```bash
./run.sh test                                   # everything below in one go
cd Backend && ./mvnw verify                     # 9 unit + 37 integration tests
cd Frontend && npm test -- --watch=false        # 36 unit tests (Vitest)
cd Frontend && npx ng lint && npx ng build
```

## Project layout

```
Backend/src/main/java/com/task/userauth/
  auth/      login, register, logout (controller, service, request records)
  user/      User entity, repository, /me endpoint, UserResponse DTO
  admin/     /api/admin/users, ADMIN only (URL rule + @PreAuthorize)
  security/  cookie token resolver, token issuing, auth cookie, CSRF handler, 401/403 JSON
  config/    security filter chains, JWT encoder/decoder, typed @ConfigurationProperties
  common/    @RestControllerAdvice, ProblemDetail helpers, PageResponse
  logging/   LoggingAspect (AOP), RequestIdFilter (correlation ID in MDC)
  ratelimit/ Bucket4j per-IP rate-limit filter + properties
  dev/       seeded test accounts (dev profile only)
Frontend/src/app/
  core/      AuthService, interceptor, guards, UserService, API error mapping
  features/  login, register, profile pages
```

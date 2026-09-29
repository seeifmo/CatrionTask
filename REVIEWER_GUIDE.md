# Reviewer Guide

This guide is for someone evaluating the project. It takes about 15 minutes: run the app, try the
flows, check the security claims, and see where each part lives in the code. For setup details and
configuration, see the [README](README.md).

---

## 1. Run it (2 minutes)

**Prerequisites:** JDK 17+, Node 22.22.3+ (or 24.15+), and a bash shell (on Windows, use Git Bash). Maven and
Docker are not needed.

```bash
./run.sh            # builds and starts backend :8080 + frontend :4200. Ctrl+C stops both.
```

On **Windows PowerShell or cmd**, use `.\run.cmd` instead. It runs the same script through Git Bash
(`.\run.cmd test`, `.\run.cmd smoke`).

The script checks the Java and Node versions and the free ports first, installs frontend
dependencies on the first run, waits for both servers to be healthy, then prints the URLs.
Logs go to `.run/`.

If 8080 or 4200 is busy, the script moves to the next free port and prints the URLs it used.
To choose ports yourself: `BACKEND_PORT=8090 FRONTEND_PORT=4300 ./run.sh`

**Test accounts** (dev profile only, defined in `Backend/src/main/resources/application-dev.yml`):

| Username | Password | Role |
|---|---|---|
| `demo` | `Demo12345` | USER |
| `admin` | `Admin12345` | ADMIN |

Other commands:

```bash
./run.sh test       # every automated check: 46 backend tests, 36 frontend tests, lint, prod build
./run.sh smoke      # 18 live API checks with curl, including the secured endpoints (app must be running)
```

---

## 2. Try it in the browser (5 minutes)

Open http://localhost:4200 and go through these:

| # | Do this | Expect |
|---|---|---|
| 1 | Open `/profile` while logged out | Redirect to `/login?returnUrl=%2Fprofile` |
| 2 | Submit the empty login form | "required" errors on the fields, no request sent |
| 3 | Log in as `demo` with a wrong password | Banner "Invalid username or password.", password field cleared |
| 4 | Log in as `demo` / `Demo12345` | Profile card with name, username, email and role |
| 5 | Reload the page | Still logged in (the session is restored from the cookie) |
| 6 | Open `/login` while logged in | Redirect back to `/profile` |
| 7 | Log out, then open `/profile` | Redirect to login (the cookie was cleared on the server) |
| 8 | Register with username `demo` | Field error "Username is already taken." (409 from the server) |
| 9 | Register with mismatched passwords | "Passwords don't match." shown before any request |
| 10 | Register a new valid account | Signed in automatically and taken to the profile |
| 11 | DevTools → device toolbar at 375px | Single column, no horizontal scroll |
| 12 | DevTools → Application → Cookies | `access_token` is **HttpOnly**, and `document.cookie` doesn't show it |

**Session expiry.** Start with a short token lifetime: stop the app, then run it with
`JAVA_OPTS="-Dapp.jwt.ttl=1m" ./run.sh`. Log in, wait about 70 seconds, then switch to another
browser tab and back. You should land on `/login?reason=expired` with a "Your session has expired"
snackbar.

---

## 3. Check the secured endpoints (3 minutes)

```bash
./run.sh smoke
```

Each check prints PASS or FAIL. The ones that matter most:

| Request | As | Expected |
|---|---|---|
| `GET /api/users/me` | no session | **401** ProblemDetail |
| `GET /api/admin/users` | no session | **401** |
| `GET /api/admin/users` | `demo` (USER) | **403** ProblemDetail |
| `GET /api/admin/users` | `admin` (ADMIN) | **200**, paged list with no password fields |
| `GET /api/admin/users?size=1000` | `admin` | **400** (page size is capped at 100) |
| `POST /api/auth/login` | without a CSRF header | **403** |
| `GET /api/users/me` | after logout | **401** |

To poke at the API by hand, Swagger UI is at http://localhost:8080/swagger-ui.html. With curl,
remember that every POST needs the CSRF double-submit header:

```bash
jar=$(mktemp)
curl -s -c "$jar" -o /dev/null http://localhost:8080/api/users/me          # gets an XSRF-TOKEN cookie
xsrf=$(awk '$6=="XSRF-TOKEN"{print $7}' "$jar")
curl -si -b "$jar" -c "$jar" -H "X-XSRF-TOKEN: $xsrf" -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"Admin12345"}' http://localhost:8080/api/auth/login
curl -s -b "$jar" http://localhost:8080/api/admin/users
```

**See the rate limiter trip** (the dev limit is 30 login/register calls per minute per IP):

```bash
for i in $(seq 1 32); do
  curl -s -o /dev/null -w '%{http_code} ' -X POST http://localhost:8080/api/auth/login
done; echo   # 403s (no CSRF token) count too, then 429s
```

That uses up the login budget for about a minute. Run `./run.sh smoke` afterwards, not before.

**See the logs:** `.run/backend.log` shows lines like
`DEBUG [3f2c…] c.t.u.auth.AuthController : -> login(LoginRequest[username=demo, password=****])`
and `INFO [3f2c…] ... : <- login 84 ms`. The bracketed ID matches the `X-Request-Id` response
header and the `requestId` in error bodies.

---

## 4. Security design: what to verify, and where

| Claim | How it works | Where to look |
|---|---|---|
| JavaScript can't steal the token (XSS) | JWT only in an `HttpOnly; SameSite=Strict; Path=/api` cookie, never in a response body | `security/AuthCookieFactory`, `auth/AuthController#login` |
| No home-made JWT code | Spring Security's OAuth2 resource server (Nimbus) checks signature (HS256 only), expiry and issuer; the token is read from the cookie | `config/JwtConfig`, `security/CookieBearerTokenResolver` |
| CSRF-safe despite cookie auth | Double-submit `XSRF-TOKEN` cookie plus `X-XSRF-TOKEN` header, which Angular sends automatically. The token isn't rotated on every request, so parallel calls don't fail | `config/SecurityConfig`, `security/SpaCsrfTokenRequestHandler`, `Frontend/src/app/app.config.ts` |
| Authorization, not just authentication | `/api/admin/**` needs ROLE_ADMIN at two layers: a URL rule **and** `@PreAuthorize`. Roles come from the signed `roles` claim | `config/SecurityConfig`, `admin/AdminUserController` |
| No username probing | Unknown user and wrong password return the same 401 message. `DaoAuthenticationProvider` also evens out the response timing | `common/GlobalExceptionHandler`, `SecurityConfig#authenticationManager` |
| Passwords stored safely | BCrypt through `DelegatingPasswordEncoder` (the `{bcrypt}` prefix allows upgrades). DTO records mean the hash never reaches JSON, and the tests assert this | `user/UserResponse`, `auth/AuthService` |
| No data leaks through sorting | The admin list uses a fixed sort, so a client can't order users by `passwordHash` | `user/UserService#listUsers` |
| Consistent, non-leaky errors | Every error is RFC 9457 `ProblemDetail`; 500s are logged but show a generic message; stack traces are off | `common/GlobalExceptionHandler`, `security/ProblemDetailSecurityHandler`, `application.yml` |
| Secrets not in code (prod) | `JWT_SECRET` must be supplied (at least 32 characters, checked at startup); DB credentials come from the environment; the cookie is `Secure` | `config/JwtProperties`, `application-prod.yml` |
| No open redirect | `returnUrl` is only followed if it's an in-app path | `Frontend/src/app/core/auth/auth.guards.ts#safeReturnUrl` |
| Brute force is throttled | Bucket4j token bucket per IP, checked **before** Spring Security. Login and register share 10/min (30 in dev); the rest of the API gets 120/min. Over the limit: 429, `Retry-After`, ProblemDetail | `ratelimit/RateLimitFilter`, `RateLimitIT` |
| Logs are useful and safe | An AOP aspect logs controller and service calls with timings. It never logs return values, masks passwords, logs tokens by type only, and strips CR/LF. Each request gets an `X-Request-Id` that shows up on every log line and in error bodies | `logging/LoggingAspect`, `logging/RequestIdFilter`, `LoggingIT` |
| One error contract | A single `@RestControllerAdvice`, plus a shared writer for filter-level errors (401/403/429), so every error has the same shape with `timestamp` and `requestId`. Log level depends on severity | `common/GlobalExceptionHandler`, `common/ProblemDetailWriter` |
| Hardened responses | `Content-Security-Policy: default-src 'none'` on the API, plus nosniff and frame denial | `SecurityConfig` (the headers are covered by a test) |
| Dev tools stay dev-only | H2 console, Swagger UI and seeded accounts exist only in the `dev` profile; everything outside `/api` and health is denied | `SecurityConfig#defaultSecurityFilterChain`, `dev/DevDataSeeder` |

---

## 5. Code tour

```
Backend/src/main/java/com/task/userauth/
  config/     SecurityConfig (two filter chains), JwtConfig, typed and validated @ConfigurationProperties
  security/   cookie token resolver, TokenService, cookie factory, SPA CSRF handler, JSON 401/403
  auth/       login / register / logout: controller, service, validated request records
  user/       User entity, repository, /api/users/me, UserResponse DTO
  admin/      /api/admin/users (ADMIN only)
  common/     @RestControllerAdvice, ProblemDetail helpers, PageResponse
  logging/    LoggingAspect (AOP), RequestIdFilter
  ratelimit/  Bucket4j per-IP RateLimitFilter
  dev/        seeded accounts (dev profile only)
  resources/db/migration/V1__create_users.sql   Flyway schema (ddl-auto=validate)

Frontend/src/app/
  core/auth/  AuthService (session signal), authInterceptor, authGuard / guestGuard
  core/http/  ProblemDetail → message and field errors
  features/   login, register, profile (standalone, OnPush, typed reactive forms)
```

**Tests:**
- `Backend/src/test`: MockMvc integration tests for every endpoint and failure mode (401, 403,
  400, 409, CSRF, expired, tampered and foreign-issuer tokens), plus unit tests and a JPA test
  against the real Flyway schema.
- `Frontend/src/**/*.spec.ts`: service, interceptor, guards and component tests (Vitest).

---

## 6. Known limitations (deliberate scope)

- **No refresh tokens.** The user signs in again after `JWT_TTL` (30 minutes by default).
- **Role changes apply on the next login.** Roles are read from the signed token, so demoting a
  user takes effect when their current token expires.
- **No token revocation list.** Logout clears the cookie, but a copied token stays valid until it
  expires. The `jti` claim is already there to support a denylist.
- **The rate limit is per IP and per instance.** There's no per-account lockout, and with several
  replicas you'd need a shared store (Bucket4j supports Redis).
- **No email verification or password reset.**
- **No admin screen in the UI.** The admin endpoint is API-only; test it with `./run.sh smoke` or Swagger.
- **The PostgreSQL `prod` profile is configured but wasn't run in this environment.** The README has the commands.

#!/usr/bin/env bash
#
# One entry point for running and checking the app.
#
#   ./run.sh            start backend + frontend (dev profile), Ctrl+C stops both
#   ./run.sh test       run every automated check (backend tests, frontend tests, lint, build)
#   ./run.sh smoke      exercise the live API with curl (start the app first)
#   ./run.sh help       show this help
#
# Environment overrides:
#   BACKEND_PORT   (default 8080)   FRONTEND_PORT (default 4200)
#   JAVA_OPTS      extra JVM flags for the backend, e.g. "-Xmx512m"
#
# Works on Linux, macOS and Windows (Git Bash).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/Backend"
FRONTEND_DIR="$ROOT/Frontend"
RUN_DIR="$ROOT/.run"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4200}"
JAVA_OPTS="${JAVA_OPTS:-}"
BACKEND_URL="http://localhost:$BACKEND_PORT"

PIDS=()

# ---------------------------------------------------------------------------- helpers

if [[ -t 1 ]]; then
  BOLD=$'\e[1m'; GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; RESET=$'\e[0m'
else
  BOLD=''; GREEN=''; RED=''; YELLOW=''; RESET=''
fi

info() { printf '%s==>%s %s\n' "$BOLD" "$RESET" "$*"; }
warn() { printf '%swarning:%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
die()  { printf '%serror:%s %s\n' "$RED" "$RESET" "$*" >&2; exit 1; }

usage() { sed -n '3,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

require_java() {
  command -v java >/dev/null 2>&1 || die "Java not found. Install JDK 17 or newer."
  local version major
  version="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  major="${version%%.*}"
  [[ "$major" == "1" ]] && major="$(echo "$version" | cut -d. -f2)"
  (( major >= 17 )) || die "Java $version found; JDK 17 or newer is required."
}

require_node() {
  command -v node >/dev/null 2>&1 || die "Node.js not found. Install Node 22 LTS (22.22.3 or newer)."
  # Same rule the Angular 22 CLI enforces: >=22.22.3 on 22.x, >=24.15.0 on 24.x, or 26+.
  if ! node -e '
    const [maj, min, pat] = process.versions.node.split(".").map(Number);
    const atLeast = (a, b, c) => min > b || (min === b && pat >= c);
    const ok = maj >= 26 || (maj === 24 && atLeast(24, 15, 0)) || (maj === 22 && atLeast(22, 22, 3));
    process.exit(ok ? 0 : 1);'; then
    die "Node $(node -v) is not supported by Angular 22. Needs 22.22.3+, 24.15+ or 26+ (e.g. 'nvm use 22.22.3')."
  fi
}

port_in_use() { (echo >"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

require_free_port() {
  if port_in_use "$1"; then
    die "Port $1 is already in use. Stop that process or set $2=<another port>."
  fi
}

wait_for() { # url, name, timeout-seconds, log file
  local url="$1" name="$2" timeout="$3" log="$4" waited=0
  until curl -fsS -o /dev/null "$url" 2>/dev/null; do
    if (( waited >= timeout )); then
      tail -n 30 "$log" >&2 || true
      die "$name did not start within ${timeout}s (log: $log)"
    fi
    sleep 2; waited=$((waited + 2))
  done
}

ensure_frontend_deps() {
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    info "Installing frontend dependencies (npm ci)"
    (cd "$FRONTEND_DIR" && npm ci --no-audit --no-fund)
  fi
}

cleanup() {
  trap - INT TERM EXIT
  if ((${#PIDS[@]})); then
    info "Stopping"
    kill "${PIDS[@]}" 2>/dev/null || true
    wait "${PIDS[@]}" 2>/dev/null || true
  fi
}

# ---------------------------------------------------------------------------- commands

cmd_dev() {
  require_java
  require_node
  require_free_port "$BACKEND_PORT" BACKEND_PORT
  require_free_port "$FRONTEND_PORT" FRONTEND_PORT
  mkdir -p "$RUN_DIR"
  trap cleanup EXIT
  trap 'cleanup; exit 130' INT TERM

  info "Building backend"
  (cd "$BACKEND_DIR" && ./mvnw -q -B -DskipTests package)
  local jar
  jar="$(ls "$BACKEND_DIR"/target/user-auth-*.jar | head -n 1)"

  info "Starting backend on $BACKEND_URL (log: .run/backend.log)"
  # shellcheck disable=SC2086 # JAVA_OPTS is intentionally word-split
  java $JAVA_OPTS -jar "$jar" --server.port="$BACKEND_PORT" >"$RUN_DIR/backend.log" 2>&1 &
  PIDS+=("$!")

  ensure_frontend_deps
  info "Starting frontend on http://localhost:$FRONTEND_PORT (log: .run/frontend.log)"
  (cd "$FRONTEND_DIR" && API_TARGET="$BACKEND_URL" \
    exec node node_modules/@angular/cli/bin/ng.js serve --port "$FRONTEND_PORT") \
    >"$RUN_DIR/frontend.log" 2>&1 &
  PIDS+=("$!")

  wait_for "$BACKEND_URL/actuator/health" "Backend" 120 "$RUN_DIR/backend.log"
  wait_for "http://localhost:$FRONTEND_PORT" "Frontend" 120 "$RUN_DIR/frontend.log"

  cat <<EOF

${GREEN}${BOLD}Ready.${RESET}
  App         http://localhost:$FRONTEND_PORT
  API         $BACKEND_URL/api
  Swagger UI  $BACKEND_URL/swagger-ui.html
  H2 console  $BACKEND_URL/h2-console   (JDBC URL jdbc:h2:mem:userauth, user sa, no password)

  Test logins (dev profile only):
    demo  / Demo12345   role USER
    admin / Admin12345  role ADMIN

  In another terminal:  BACKEND_PORT=$BACKEND_PORT ./run.sh smoke
  Press Ctrl+C to stop.
EOF
  # Exit (and clean up) as soon as either process dies. Polling instead of `wait -n` keeps this
  # working on the bash 3.2 that ships with macOS.
  while kill -0 "${PIDS[0]}" 2>/dev/null && kill -0 "${PIDS[1]}" 2>/dev/null; do
    sleep 2
  done
  warn "A process exited; see the logs in .run/"
}

cmd_test() {
  require_java
  require_node

  info "Backend: unit + integration tests (mvnw verify)"
  (cd "$BACKEND_DIR" && ./mvnw -B verify)

  ensure_frontend_deps
  info "Frontend: unit tests"
  (cd "$FRONTEND_DIR" && npx ng test --watch=false)
  info "Frontend: lint"
  (cd "$FRONTEND_DIR" && npx ng lint)
  info "Frontend: production build"
  (cd "$FRONTEND_DIR" && npx ng build)

  printf '\n%s%sAll checks passed.%s\n' "$GREEN" "$BOLD" "$RESET"
}

cmd_smoke() {
  command -v curl >/dev/null 2>&1 || die "curl is required."
  curl -fsS -o /dev/null "$BACKEND_URL/actuator/health" 2>/dev/null \
    || die "Backend is not running on $BACKEND_URL. Start it with ./run.sh (or set BACKEND_PORT)."

  local jar failures=0
  SMOKE_TMP="$(mktemp -d)"
  trap 'rm -rf "$SMOKE_TMP"' EXIT
  local tmp="$SMOKE_TMP"
  jar="$tmp/cookies.txt"

  # call METHOD PATH [JSON]; prints the HTTP status. Sends the CSRF header the way Angular does.
  call() {
    local method="$1" path="$2" body="${3:-}" xsrf
    xsrf="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar" 2>/dev/null | tail -n 1)"
    local args=(-s -o "$tmp/body" -w '%{http_code}' -b "$jar" -c "$jar" -X "$method")
    [[ -n "$xsrf" ]] && args+=(-H "X-XSRF-TOKEN: $xsrf")
    [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data "$body")
    curl "${args[@]}" "$BACKEND_URL$path"
  }

  check() { # description, expected status, actual status
    if [[ "$2" == "$3" ]]; then
      printf '  %sPASS%s  %-58s %s\n' "$GREEN" "$RESET" "$1" "$3"
    else
      printf '  %sFAIL%s  %-58s expected %s, got %s\n' "$RED" "$RESET" "$1" "$2" "$3"
      failures=$((failures + 1))
    fi
  }

  info "Smoke test against $BACKEND_URL"
  check "GET  /actuator/health (public)"                          200 "$(call GET /actuator/health)"
  curl -s -D "$tmp/headers" -o /dev/null "$BACKEND_URL/api/users/me"
  grep -qi '^x-request-id:' "$tmp/headers" && grep -qi '^x-ratelimit-remaining:' "$tmp/headers"     && check "  X-Request-Id and X-RateLimit-Remaining headers"   ok ok     || check "  X-Request-Id and X-RateLimit-Remaining headers"   ok missing
  check "GET  /api/users/me without a session"                    401 "$(call GET /api/users/me)"
  check "GET  /api/admin/users without a session"                 401 "$(call GET /api/admin/users)"

  # Deliberately no CSRF header: a fresh jar has no XSRF cookie yet.
  check "POST /api/auth/login without CSRF token"                 403 \
    "$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
        --data '{"username":"demo","password":"Demo12345"}' "$BACKEND_URL/api/auth/login")"

  check "POST /api/auth/login wrong password"                     401 \
    "$(call POST /api/auth/login '{"username":"demo","password":"Wrong1234"}')"
  check "POST /api/auth/register invalid input"                   400 \
    "$(call POST /api/auth/register '{"username":"x","email":"bad","fullName":"","password":"short"}')"
  check "POST /api/auth/register taken username"                  409 \
    "$(call POST /api/auth/register '{"username":"demo","email":"new@example.com","fullName":"D","password":"Passw0rd1"}')"

  check "POST /api/auth/login as demo (USER)"                     204 \
    "$(call POST /api/auth/login '{"username":"demo","password":"Demo12345"}')"
  grep -q 'access_token' "$jar" && ! grep -q 'access_token' "$tmp/body" \
    && check "  token only in the HttpOnly cookie, not the body"   ok ok \
    || check "  token only in the HttpOnly cookie, not the body"   ok missing
  check "GET  /api/users/me as demo"                              200 "$(call GET /api/users/me)"
  grep -qi 'password' "$tmp/body" \
    && check "  profile JSON has no password field"               ok leaked \
    || check "  profile JSON has no password field"               ok ok
  check "GET  /api/admin/users as demo (USER)"                    403 "$(call GET /api/admin/users)"

  check "POST /api/auth/login as admin (ADMIN)"                   204 \
    "$(call POST /api/auth/login '{"username":"admin","password":"Admin12345"}')"
  check "GET  /api/admin/users as admin"                          200 "$(call GET /api/admin/users)"
  check "GET  /api/admin/users?size=1000 (size capped at 100)"    400 "$(call GET '/api/admin/users?size=1000')"

  check "POST /api/auth/logout"                                   204 "$(call POST /api/auth/logout)"
  check "GET  /api/users/me after logout"                         401 "$(call GET /api/users/me)"

  echo
  if (( failures )); then
    die "$failures check(s) failed."
  fi
  printf '%s%sAll smoke checks passed.%s\n' "$GREEN" "$BOLD" "$RESET"
}

# ---------------------------------------------------------------------------- main

case "${1:-dev}" in
  dev)   cmd_dev ;;
  test)  cmd_test ;;
  smoke) cmd_smoke ;;
  help|-h|--help) usage ;;
  *) usage; exit 2 ;;
esac

# User Portal: Angular frontend

See the [root README](../README.md) for setup, architecture and the API.

```bash
npm ci
npm start                  # http://localhost:4200, proxies /api to http://localhost:8080 (override with API_TARGET)
npm test -- --watch=false  # unit tests (Vitest)
npx ng lint
npx ng build               # output in dist/user-auth-ui/browser
```

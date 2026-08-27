# Changelog

All notable changes to this project are documented in this file.

## 1.1.0 - Initial release

Initial release of `com.nibub:globbook-auth`, a dependency-free Java
client SDK for "Sign in with Globbook". Versioned at 1.1.0 from the start
to match the current generation of the sibling Go/JS/PHP/Python SDKs,
which include this version's restricted-claims and CSRF-state support
natively rather than as a later addition.

- `GlobbookAuthClient(Config)` — server-side OAuth client, `Config` built
  via a fluent `Builder`, validated at construction time.
- `GlobbookAuthClient.getAuthorizationUrl(AuthorizationUrlOptions)` to
  build the redirect URL for step 1 of the OAuth flow
  (`GET /api/v2/oauth/authorize`), supporting:
  - `AuthorizationUrlOptions.Builder.scopes(...)` — request restricted
    claims (`Scope.BIRTHDATE`, `Scope.GENDER`, `Scope.PHONE`, `Scope.ADDRESS`).
  - `AuthorizationUrlOptions.Builder.state(...)` — optional CSRF
    protection (RFC 6749 §10.12), echoed back by `parseCallbackParams`.
- `GlobbookAuthClient.parseCallbackParams(String)` (static) to extract
  `code`/`state` from a callback request — accepts a full URL, path+query,
  or bare query string.
- `GlobbookAuthClient.exchangeCodeForToken(String)` implementing the token
  exchange (`POST /api/v2/oauth/token`, `application/x-www-form-urlencoded`),
  returning a `Token` record.
- `GlobbookAuthClient.getUserInfo(String)` implementing the userinfo fetch
  (`GET /api/v2/oauth/userinfo`), returning a `UserInfo` with OIDC-style
  top-level fields plus nullable restricted claims (`birthdate`, `gender`,
  `phoneNumber`, `address`) — `null` unless the app is verified and the
  user granted the matching scope.
- `AuthError` (unchecked, extends `RuntimeException`) carrying the OAuth
  `error`/`error_description`/HTTP status, plus SDK-raised codes
  (`timeout`, `network_error`, `invalid_response`) for transport-level
  failures.
- Configurable `baseUrl` (defaults to `https://globbook.com`) and
  `timeout` (defaults to 10s, a `java.time.Duration`) bounding every
  request.
- Secret redaction: `Config.toString()`/`Token.toString()` never leak
  `clientSecret`/`accessToken`.
- Zero third-party runtime dependencies — built on
  `java.net.http.HttpClient` (stdlib since Java 11) and a minimal internal
  JSON reader scoped to this SDK's flat response shapes, avoiding a
  Jackson/Gson dependency. Requires Java 17+ (records; the Spring Boot 3.x
  LTS baseline).

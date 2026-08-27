# globbook-auth-java

A dependency-free Java client SDK for **"Sign in with Globbook"** — the
OAuth 2.0-style authorization-code flow exposed by Globbook's backend at
`/api/v2/oauth/*`.

Use this library to let users of your Java server-side application (Spring
Boot, Jakarta EE, or plain servlets) sign in with their Globbook account.
It wraps the three real HTTP calls the flow requires (authorization
redirect, token exchange, userinfo fetch) behind a small, idiomatic Java
API, built entirely on `java.net.http.HttpClient` — zero third-party
runtime dependencies.

> **Note**: this package does not register applications with Globbook.
> Before using it you must create an app in Globbook's developer console
> to obtain a `clientId`, `clientSecret`, and register your app's
> `redirectUrl` — that is a one-time manual step, unrelated to this SDK.

## Installation

Requires **Java 17** or later (records, and the LTS baseline for Spring
Boot 3.x).

### Maven

```xml
<dependency>
    <groupId>com.nibub</groupId>
    <artifactId>globbook-auth</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation("com.nibub:globbook-auth:1.1.0")
```

## Quickstart

The full flow has three steps: redirect the user to Globbook, receive the
callback and exchange the code for a token, then fetch the user's profile.
Here's a Spring Boot `@RestController` example:

```java
@RestController
public class GlobbookAuthController {

    private final GlobbookAuthClient client;

    public GlobbookAuthController(@Value("${globbook.client-id}") String clientId,
                                   @Value("${globbook.client-secret}") String clientSecret) {
        this.client = new GlobbookAuthClient(Config.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUrl("https://yourapp.com/auth/globbook/callback")
                // .baseUrl("https://staging.globbook.com") // optional, defaults to https://globbook.com
                .build());
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute("oauth_state", state);
        String url = client.getAuthorizationUrl(AuthorizationUrlOptions.builder().state(state).build());
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
    }

    @GetMapping("/auth/globbook/callback")
    public ResponseEntity<String> callback(HttpServletRequest request, HttpSession session) {
        CallbackParams params = GlobbookAuthClient.parseCallbackParams(request.getRequestURI() + "?" + request.getQueryString());
        String expectedState = (String) session.getAttribute("oauth_state");
        if (params.code() == null || !Objects.equals(params.state(), expectedState)) {
            return ResponseEntity.badRequest().body("Invalid or missing state -- possible CSRF.");
        }

        Token token = client.exchangeCodeForToken(params.code());
        UserInfo user = client.getUserInfo(token.accessToken());

        // Look up or create a local account keyed on user.getSub(), then
        // establish your own session. getSub() is a stable md5 hash
        // identifying the Globbook user -- not their raw numeric ID.
        session.setAttribute("userId", user.getSub());
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/dashboard").build();
    }
}
```

A runnable, framework-free version of this example (using the JDK's
built-in `HttpServer`) lives in
[`example/src/main/java/ExampleApp.java`](./example/src/main/java/ExampleApp.java).

## API reference

### `class Config`

Build with `Config.builder()`:

| Field           | Required | Description                                                                 |
| --------------- | -------- | ---------------------------------------------------------------------------- |
| `clientId`      | yes      | App ID from Globbook's developer console.                                    |
| `clientSecret`  | yes      | Confidential secret from Globbook's developer console. Server-side only.    |
| `redirectUrl`   | yes      | Your app's callback URL, exactly as registered with Globbook.               |
| `baseUrl`       | no       | Globbook API origin. Defaults to `https://globbook.com`.                    |
| `timeout`       | no       | `java.time.Duration` per-request timeout. Defaults to 10 seconds.           |

```java
Config config = Config.builder()
        .clientId("...")
        .clientSecret("...")
        .redirectUrl("https://yourapp.com/auth/globbook/callback")
        .build();
```

Throws `IllegalArgumentException` synchronously if `clientId`,
`clientSecret`, or `redirectUrl` is missing or blank — validation happens
at construction time, not deferred to the first API call.

### `class GlobbookAuthClient`

```java
GlobbookAuthClient client = new GlobbookAuthClient(config);
```

#### `String getAuthorizationUrl(AuthorizationUrlOptions options)`

Builds the URL to redirect the user's browser to, to start the sign-in
flow. Does not make an HTTP request itself. Use `AuthorizationUrlOptions.none()`
for the base profile with no CSRF-protection state.

```java
AuthorizationUrlOptions options = AuthorizationUrlOptions.builder()
        .scopes(Scope.BIRTHDATE, Scope.GENDER)
        .state(csrfToken)
        .build();
String url = client.getAuthorizationUrl(options);
```

#### `static CallbackParams parseCallbackParams(String urlOrQuery)`

Static method. Extracts the authorization code (and CSRF state, if
present) from a callback request's URL/query string. Framework-agnostic —
pass a full URL, a path+query string, or a bare query string.

```java
public record CallbackParams(String code, String state) {}
```

#### `Token exchangeCodeForToken(String code)`

Exchanges an authorization code for an access token via
`POST /api/v2/oauth/token` (sent as `application/x-www-form-urlencoded`,
the only content type Globbook's token endpoint accepts). Throws
`AuthError` on failure.

```java
public record Token(String accessToken, String tokenType, int expiresIn) {}
```

#### `UserInfo getUserInfo(String accessToken)`

Fetches the authenticated user's profile via `GET /api/v2/oauth/userinfo`.
Throws `AuthError` on failure.

```java
class UserInfo {
    String getSub();                // OIDC "sub" -- an md5 hash, not the numeric user id
    String getPreferredUsername();
    boolean isProfileVerified();
    String getEmail();
    String getName();
    String getGivenName();
    String getFamilyName();
    String getBio();
    String getPicture();            // signed CDN URL, or null
    String getCoverImage();         // signed CDN URL, or null
    String getWebsite();

    // Restricted claims -- see "Restricted claims" below
    String getBirthdate();          // YYYY-MM-DD, or null
    String getGender();
    String getPhoneNumber();
    String getAddress();            // "city country" -- this platform has no street address
}
```

### Restricted claims

`birthdate`, `gender`, `phoneNumber`, and `address` are gated separately
from the rest of the profile. Globbook only populates them — the getter
returns `null` otherwise — when **both** are true:

1. Your app has been verified in the Globbook Developer Console.
2. You requested the scope via `AuthorizationUrlOptions.builder().scopes(...)`,
   **and** the signed-in user granted it on the consent screen —
   requesting a scope is not the same as receiving it; the user can
   uncheck any scope individually.

An unverified app never receives these fields, regardless of what scopes
it requests or what the user approves on consent. Always null-check before
use.

### CSRF protection (state)

Pass `state` to `AuthorizationUrlOptions` to protect against login CSRF
(RFC 6749 §10.12): an attacker who obtains their own valid authorization
code could otherwise trick a victim's browser into completing the
attacker's login on the victim's session.

```java
// Before redirecting -- generate an unguessable value and store it
// (session, signed cookie) tied to the current browser session.
String csrfToken = UUID.randomUUID().toString();
session.setAttribute("oauth_state", csrfToken);

String url = client.getAuthorizationUrl(AuthorizationUrlOptions.builder().state(csrfToken).build());

// In your callback handler -- compare before exchanging the code.
CallbackParams params = GlobbookAuthClient.parseCallbackParams(requestUrl);
if (params.code() == null || !Objects.equals(params.state(), session.getAttribute("oauth_state"))) {
    throw new IllegalStateException("invalid or missing state -- possible CSRF");
}
```

`state` is entirely optional and Globbook never interprets it — it's
echoed back unchanged, per the RFC 6749 `state` parameter. Omitting it
does not change any other behavior; this is opt-in hardening, not a
required step.

## Error handling

Every failed API call (`exchangeCodeForToken`, `getUserInfo`) throws an
`AuthError`:

```java
try {
    Token token = client.exchangeCodeForToken(code);
} catch (AuthError e) {
    switch (e.getCode()) {
        case AuthError.Codes.INVALID_GRANT -> { /* code was invalid, expired, or already used -- restart the flow */ }
        case AuthError.Codes.INVALID_REQUEST -> { /* a required field was missing -- almost certainly a bug in your integration */ }
        default -> log.error("globbook oauth error: {}: {}", e.getCode(), e.getDescription());
    }
}
```

Known error codes (all exported as `AuthError.Codes` constants):

| Code                       | Meaning                                                                 |
| --------------------------- | ------------------------------------------------------------------------ |
| `invalid_request`           | A required field was missing/malformed.                                  |
| `invalid_grant`              | `client_id`/`client_secret`/`code` combination rejected.                 |
| `invalid_token`              | The access token given to `getUserInfo` is missing/malformed/expired.    |
| `unsupported_media_type`     | The request wasn't sent as `application/x-www-form-urlencoded`. Should never occur from this SDK itself. |
| `timeout`                    | The request exceeded `Config.timeout`. Raised by this SDK, not the API. |
| `network_error`              | The request failed before reaching the API (DNS, TLS, connection refused). Raised by this SDK. |
| `invalid_response`           | Globbook returned a non-JSON or unexpectedly-shaped body.                |

## Security notes

- **`clientSecret` is a server-side secret.** Never embed it in a mobile
  app, browser bundle, or any client-side code — only call this SDK from
  your backend. `Config.toString()`/`Token.toString()` redact their secret
  field, but that is a safety net, not a substitute for keeping it out of
  client-side code in the first place.
- **`Token.accessToken()` is a bearer credential.** Treat it like a
  password: don't log it, don't put it in a URL, transmit it only over
  HTTPS.
- Every request made by this SDK is bounded by `Config.timeout` (default
  10s) so a slow or unresponsive Globbook endpoint can't hang your request
  handler indefinitely.

## Testing

```sh
mvn test
```

Tests run entirely offline against a local loopback `HttpServer` (JDK
built-in, `com.sun.net.httpserver`) — no external test dependencies beyond
JUnit 5.

## License

MIT — see [LICENSE](./LICENSE).

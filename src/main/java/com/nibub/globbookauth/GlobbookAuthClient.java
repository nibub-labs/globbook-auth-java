package com.nibub.globbookauth;

import com.nibub.globbookauth.internal.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

/**
 * Server-side client for "Sign in with Globbook" OAuth 2.0.
 *
 * <p>Typical flow:
 * <ol>
 *   <li>Build a redirect URL with {@link #getAuthorizationUrl} and send the user's browser there.</li>
 *   <li>Globbook redirects back to your {@code redirectUrl} with {@code ?code=...} (and {@code ?state=...}
 *       if you sent one) — parse it with {@link #parseCallbackParams}.</li>
 *   <li>Exchange that code for an access token with {@link #exchangeCodeForToken}.</li>
 *   <li>Fetch the user's profile with {@link #getUserInfo}.</li>
 * </ol>
 *
 * <p><b>SECURITY</b>: only ever construct this class in server-side code. It requires
 * {@code clientSecret}, which must never be shipped to a browser bundle or a mobile app.
 *
 * <p>This client is thread-safe and safe to reuse across multiple requests/users — it holds no
 * per-user mutable state after construction.
 */
public final class GlobbookAuthClient {

    private final Config config;
    private final HttpClient httpClient;

    public GlobbookAuthClient(Config config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout().isZero() ? Duration.ofSeconds(30) : config.getTimeout())
                .build();
    }

    /**
     * Builds the URL to redirect the user's browser to for the Globbook-hosted consent screen.
     * Makes no network request — your handler is responsible for issuing the actual redirect.
     *
     * @param options see {@link AuthorizationUrlOptions}; use {@link AuthorizationUrlOptions#none()}
     *                for the base profile with no CSRF-protection state.
     * @return the full authorization URL, e.g. {@code https://globbook.com/api/v2/oauth/authorize?client_id=...}.
     */
    public String getAuthorizationUrl(AuthorizationUrlOptions options) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        if (!options.getScopes().isEmpty()) {
            params.put("scope", String.join(" ", options.getScopes()));
        }
        if (options.getState() != null && !options.getState().isEmpty()) {
            params.put("state", options.getState());
        }
        return config.getBaseUrl() + "/api/v2/oauth/authorize?" + encodeForm(params);
    }

    /**
     * Extracts the authorization code (and CSRF-protection {@code state}, if present) from the
     * query string of the callback request Globbook redirects the user's browser to after they
     * approve (or deny) the consent screen.
     *
     * @param urlOrQuery a full URL, a path with query string, or a bare query string (with or
     *                   without a leading {@code ?}).
     * @return a {@link CallbackParams} — both fields are {@code null} if not present. If you passed
     *         {@code state} to {@link #getAuthorizationUrl}, compare the returned {@code state}
     *         against what you stored before redirecting and reject the callback on a mismatch.
     */
    public static CallbackParams parseCallbackParams(String urlOrQuery) {
        String query = urlOrQuery;
        int qIdx = urlOrQuery.indexOf('?');
        if (qIdx != -1) {
            query = urlOrQuery.substring(qIdx + 1);
        }
        Map<String, String> parsed = decodeForm(query);
        return new CallbackParams(parsed.get("code"), parsed.get("state"));
    }

    /**
     * Exchanges an authorization code (from {@link #parseCallbackParams}) for an access token via
     * {@code POST /api/v2/oauth/token}. Sent as {@code application/x-www-form-urlencoded} — the
     * only content type Globbook's token endpoint accepts.
     *
     * @param code the {@code code} value received in the OAuth callback.
     * @return the issued {@link Token}.
     * @throws AuthError if {@code code} is empty, the request fails at the transport level, or the
     *                    API rejects it (e.g. {@code invalid_grant} for an expired/already-used code).
     */
    public Token exchangeCodeForToken(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new AuthError(AuthError.Codes.INVALID_REQUEST, "exchangeCodeForToken: 'code' must not be empty");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", config.getClientId());
        form.put("client_secret", config.getClientSecret());
        form.put("code", code);

        Map<String, Object> body = post(config.getBaseUrl() + "/api/v2/oauth/token", encodeForm(form));

        return new Token(
                stringOf(body, "access_token"),
                stringOf(body, "token_type"),
                (int) doubleOf(body, "expires_in")
        );
    }

    /**
     * Fetches the authenticated user's profile via {@code GET /api/v2/oauth/userinfo} using
     * {@code Authorization: Bearer {accessToken}}.
     *
     * @param accessToken the access token from {@link #exchangeCodeForToken}.
     * @return the user's {@link UserInfo}.
     * @throws AuthError if {@code accessToken} is empty, the request fails at the transport level,
     *                    or the API rejects it ({@code invalid_token} — missing, malformed, or expired).
     */
    public UserInfo getUserInfo(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new AuthError(AuthError.Codes.INVALID_REQUEST, "getUserInfo: 'accessToken' must not be empty");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/api/v2/oauth/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .timeout(config.getTimeout().isZero() ? Duration.ofDays(1) : config.getTimeout())
                .build();

        Map<String, Object> body = send(request);

        UserInfo.Builder b = UserInfo.builder();
        b.sub = stringOf(body, "sub");
        b.preferredUsername = stringOf(body, "preferred_username");
        b.profileVerified = body.get("profile_verified") == Boolean.TRUE;
        b.email = stringOf(body, "email");
        b.name = stringOf(body, "name");
        b.givenName = stringOf(body, "given_name");
        b.familyName = stringOf(body, "family_name");
        b.bio = stringOf(body, "bio");
        b.picture = nullableStringOf(body, "picture");
        b.coverImage = nullableStringOf(body, "cover_image");
        b.website = stringOf(body, "website");
        // Restricted claims are omitted from the response entirely (not sent as empty strings)
        // unless the app is verified and the user granted the matching scope — a missing key
        // already yields null via nullableStringOf, so no extra coercion is needed here.
        b.birthdate = nullableStringOf(body, "birthdate");
        b.gender = nullableStringOf(body, "gender");
        b.phoneNumber = nullableStringOf(body, "phone_number");
        b.address = nullableStringOf(body, "address");
        return b.build();
    }

    private Map<String, Object> post(String url, String formBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.US_ASCII))
                .timeout(config.getTimeout().isZero() ? Duration.ofDays(1) : config.getTimeout())
                .build();
        return send(request);
    }

    private Map<String, Object> send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (isTimeout(e)) {
                throw new AuthError(AuthError.Codes.TIMEOUT,
                        "Request to Globbook timed out after " + config.getTimeout(), null, e);
            }
            throw new AuthError(AuthError.Codes.NETWORK_ERROR, "Request to Globbook failed: " + e.getMessage(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthError(AuthError.Codes.NETWORK_ERROR, "Request to Globbook was interrupted", null, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw errorFromBody(response.body(), status);
        }
        return decodeBody(response.body(), status);
    }

    private static boolean isTimeout(IOException e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof TimeoutException || cur instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static Map<String, Object> decodeBody(String raw, int status) {
        try {
            return Json.parseObject(raw);
        } catch (RuntimeException e) {
            throw new AuthError(AuthError.Codes.INVALID_RESPONSE,
                    "Globbook API returned a non-JSON response (HTTP " + status + ")", status, e);
        }
    }

    private static AuthError errorFromBody(String raw, int status) {
        Map<String, Object> decoded;
        try {
            decoded = Json.parseObject(raw);
        } catch (RuntimeException e) {
            return new AuthError(AuthError.Codes.INVALID_RESPONSE,
                    "Globbook API returned a non-JSON error response (HTTP " + status + ")", status, e);
        }
        if (!decoded.containsKey("error")) {
            return new AuthError(AuthError.Codes.INVALID_RESPONSE,
                    "Globbook API returned an unexpected error shape (HTTP " + status + ")", status);
        }
        String code = stringOf(decoded, "error");
        String description = stringOf(decoded, "error_description");
        return new AuthError(code, description, status);
    }

    private static String stringOf(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }

    private static String nullableStringOf(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static double doubleOf(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof Double ? (Double) v : 0.0;
    }

    private static String encodeForm(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : params.entrySet()) {
            joiner.add(urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()));
        }
        return joiner.toString();
    }

    private static Map<String, String> decodeForm(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq == -1 ? pair : pair.substring(0, eq);
            String value = eq == -1 ? "" : pair.substring(eq + 1);
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}

package com.nibub.globbookauth;

/**
 * An OAuth-standard error returned by Globbook's authorization server (the
 * token and userinfo endpoints), per RFC 6749 §5.2 — or a transport-level
 * failure normalized into the same shape (see {@link Codes} for which
 * codes are which).
 *
 * <p>Every failure from {@link GlobbookAuthClient#exchangeCodeForToken} and
 * {@link GlobbookAuthClient#getUserInfo} throws this (never a raw
 * {@code IOException}/timeout/JSON-decode exception), so calling code only
 * ever needs to catch one exception type.
 *
 * <pre>{@code
 * try {
 *     Token token = client.exchangeCodeForToken(code);
 * } catch (AuthError e) {
 *     if (AuthError.Codes.INVALID_GRANT.equals(e.getCode())) {
 *         // code was invalid, expired, or already used — restart the flow
 *     } else {
 *         log.error("globbook oauth error: {}: {}", e.getCode(), e.getDescription());
 *     }
 * }
 * }</pre>
 */
public final class AuthError extends RuntimeException {

    /** Well-known error codes — see the individual field docs for which are OAuth-standard vs. SDK-raised. */
    public static final class Codes {
        private Codes() {
        }

        /** A required field was missing/malformed in the request. Returned with HTTP 400. */
        public static final String INVALID_REQUEST = "invalid_request";

        /** The client_id/client_secret/code combination was rejected — wrong secret, or an expired/already-used code. Returned with HTTP 401. */
        public static final String INVALID_GRANT = "invalid_grant";

        /** The access token presented to the userinfo endpoint was missing, malformed, expired, or revoked. Returned with HTTP 401. */
        public static final String INVALID_TOKEN = "invalid_token";

        /** The token request's Content-Type was not application/x-www-form-urlencoded. Returned with HTTP 415. */
        public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";

        /** Raised by this SDK (never by the Globbook API) when a request exceeds its configured timeout. */
        public static final String TIMEOUT = "timeout";

        /** Raised by this SDK (never by the Globbook API) when the HTTP request itself fails — DNS, TLS, connection refused. */
        public static final String NETWORK_ERROR = "network_error";

        /** Raised by this SDK (never by the Globbook API) when the response body isn't valid JSON, or isn't shaped as expected. */
        public static final String INVALID_RESPONSE = "invalid_response";
    }

    private final String code;
    private final String description;
    private final Integer statusCode;

    public AuthError(String code, String description) {
        this(code, description, null);
    }

    public AuthError(String code, String description, Integer statusCode) {
        super(buildMessage(code, description));
        this.code = code;
        this.description = description == null ? "" : description;
        this.statusCode = statusCode;
    }

    public AuthError(String code, String description, Integer statusCode, Throwable cause) {
        super(buildMessage(code, description), cause);
        this.code = code;
        this.description = description == null ? "" : description;
        this.statusCode = statusCode;
    }

    private static String buildMessage(String code, String description) {
        if (description == null || description.isEmpty()) {
            return "globbookauth: " + code;
        }
        return "globbookauth: " + code + " (" + description + ")";
    }

    /** The machine-readable error code — see {@link Codes}. */
    public String getCode() {
        return code;
    }

    /** Human-readable description, as returned by the API (or synthesized by this SDK for transport failures). Safe to log. */
    public String getDescription() {
        return description;
    }

    /** The HTTP status code of the failing response, or {@code null} for a pure transport-level failure that never received a response. */
    public Integer getStatusCode() {
        return statusCode;
    }
}

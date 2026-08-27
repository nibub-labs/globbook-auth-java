package com.nibub.globbookauth;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings passed to the {@link GlobbookAuthClient} constructor.
 *
 * <p>{@code clientId}, {@code clientSecret}, and {@code redirectUrl} are
 * all required and validated synchronously — construction throws
 * {@link IllegalArgumentException} immediately if any is missing or blank,
 * rather than deferring the failure to the first API call.
 *
 * <p>Build one with the fluent {@link Builder}:
 *
 * <pre>{@code
 * Config config = Config.builder()
 *     .clientId(System.getenv("GLOBBOOK_CLIENT_ID"))
 *     .clientSecret(System.getenv("GLOBBOOK_CLIENT_SECRET"))
 *     .redirectUrl("https://yourapp.com/auth/globbook/callback")
 *     .build();
 * }</pre>
 */
public final class Config {

    /** Production Globbook API origin, used when {@code baseUrl} is not supplied. */
    public static final String DEFAULT_BASE_URL = "https://globbook.com";

    /** Default request timeout, used when {@code timeout} is not supplied. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String clientId;
    private final String clientSecret;
    private final String redirectUrl;
    private final String baseUrl;
    private final Duration timeout;

    private Config(Builder b) {
        this.clientId = requireNonBlank(b.clientId, "clientId");
        this.clientSecret = requireNonBlank(b.clientSecret, "clientSecret");
        this.redirectUrl = requireNonBlank(b.redirectUrl, "redirectUrl");
        String base = (b.baseUrl == null || b.baseUrl.isEmpty()) ? DEFAULT_BASE_URL : b.baseUrl;
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.timeout = b.timeout == null ? DEFAULT_TIMEOUT : b.timeout;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("globbookauth: " + name + " is required and must be a non-empty string");
        }
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Your app's client ID, from the Globbook developer console. */
    public String getClientId() {
        return clientId;
    }

    /** Your app's client secret. Server-side only — see the package README's Security section. */
    public String getClientSecret() {
        return clientSecret;
    }

    /** Your app's callback URL, exactly as registered with Globbook. */
    public String getRedirectUrl() {
        return redirectUrl;
    }

    /** Globbook API origin. Defaults to {@link #DEFAULT_BASE_URL}, with any trailing slash stripped. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Timeout applied to every request this client makes. Defaults to {@link #DEFAULT_TIMEOUT} (10s). */
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        // Redact clientSecret so an accidental toString()/log of a Config never leaks it.
        return "Config{clientId='" + clientId + "', clientSecret='[REDACTED]', redirectUrl='" + redirectUrl
                + "', baseUrl='" + baseUrl + "', timeout=" + timeout + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Config)) return false;
        Config other = (Config) o;
        return clientId.equals(other.clientId)
                && clientSecret.equals(other.clientSecret)
                && redirectUrl.equals(other.redirectUrl)
                && baseUrl.equals(other.baseUrl)
                && timeout.equals(other.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, clientSecret, redirectUrl, baseUrl, timeout);
    }

    public static final class Builder {
        private String clientId;
        private String clientSecret;
        private String redirectUrl;
        private String baseUrl;
        private Duration timeout;

        private Builder() {
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder redirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
            return this;
        }

        /** Optional. Defaults to {@link Config#DEFAULT_BASE_URL} when unset. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Optional. Defaults to {@link Config#DEFAULT_TIMEOUT} (10s) when unset. Pass {@link Duration#ZERO} to disable. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}

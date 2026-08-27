package com.nibub.globbookauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Options for {@link GlobbookAuthClient#getAuthorizationUrl}. Build with
 * {@link #builder()}, or use {@link #none()} for the base profile with no
 * CSRF-protection state.
 */
public final class AuthorizationUrlOptions {

    private static final AuthorizationUrlOptions NONE = new AuthorizationUrlOptions(Collections.emptyList(), null);

    private final List<String> scopes;
    private final String state;

    private AuthorizationUrlOptions(List<String> scopes, String state) {
        this.scopes = scopes;
        this.state = state;
    }

    /** No restricted scopes, no CSRF state — the base profile only. */
    public static AuthorizationUrlOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Restricted scopes requested in addition to the base profile — see
     * {@link Scope}. Requesting a scope only has an effect if the app is
     * verified in the Globbook Developer Console.
     */
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * The opaque CSRF-protection value, or {@code null} if none was set.
     * Globbook echoes this back unchanged on the redirect to your
     * {@code redirectUrl} (RFC 6749 §10.12).
     */
    public String getState() {
        return state;
    }

    public static final class Builder {
        private final List<String> scopes = new ArrayList<>();
        private String state;

        private Builder() {
        }

        /** Adds restricted scopes to request — see {@link Scope}. */
        public Builder scopes(String... scopes) {
            Collections.addAll(this.scopes, scopes);
            return this;
        }

        /**
         * An opaque value you generate before redirecting the user to
         * Globbook — echoed back unchanged in the {@code state} query
         * parameter on the redirect to your {@code redirectUrl}, so
         * {@link GlobbookAuthClient#parseCallbackParams} can hand it back
         * to you to compare against what you stored before the redirect
         * (RFC 6749 §10.12 CSRF protection). Globbook never interprets
         * this value itself. Optional; omit to skip CSRF protection.
         */
        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public AuthorizationUrlOptions build() {
            return new AuthorizationUrlOptions(Collections.unmodifiableList(new ArrayList<>(scopes)), state);
        }
    }
}

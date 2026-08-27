package com.nibub.globbookauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationUrlTest {

    private GlobbookAuthClient client(String baseUrl) {
        Config.Builder b = Config.builder()
                .clientId("client-123")
                .clientSecret("secret")
                .redirectUrl("https://example.com/callback");
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return new GlobbookAuthClient(b.build());
    }

    @Test
    void defaultBaseUrl() {
        GlobbookAuthClient client = client(null);
        assertEquals(
                "https://globbook.com/api/v2/oauth/authorize?client_id=client-123",
                client.getAuthorizationUrl(AuthorizationUrlOptions.none())
        );
    }

    @Test
    void customBaseUrl() {
        GlobbookAuthClient client = client("https://staging.globbook.com");
        assertEquals(
                "https://staging.globbook.com/api/v2/oauth/authorize?client_id=client-123",
                client.getAuthorizationUrl(AuthorizationUrlOptions.none())
        );
    }

    @Test
    void withScopes() {
        GlobbookAuthClient client = client(null);
        AuthorizationUrlOptions opts = AuthorizationUrlOptions.builder()
                .scopes(Scope.BIRTHDATE, Scope.GENDER)
                .build();
        assertEquals(
                "https://globbook.com/api/v2/oauth/authorize?client_id=client-123&scope=birthdate+gender",
                client.getAuthorizationUrl(opts)
        );
    }

    @Test
    void withState() {
        GlobbookAuthClient client = client(null);
        AuthorizationUrlOptions opts = AuthorizationUrlOptions.builder().state("csrf-token-123").build();
        assertEquals(
                "https://globbook.com/api/v2/oauth/authorize?client_id=client-123&state=csrf-token-123",
                client.getAuthorizationUrl(opts)
        );
    }

    @Test
    void withScopesAndState() {
        GlobbookAuthClient client = client(null);
        AuthorizationUrlOptions opts = AuthorizationUrlOptions.builder()
                .scopes(Scope.BIRTHDATE, Scope.GENDER)
                .state("csrf-token-123")
                .build();
        assertEquals(
                "https://globbook.com/api/v2/oauth/authorize?client_id=client-123&scope=birthdate+gender&state=csrf-token-123",
                client.getAuthorizationUrl(opts)
        );
    }

    @Test
    void noneMatchesEmptyBuilder() {
        GlobbookAuthClient client = client(null);
        assertEquals(
                client.getAuthorizationUrl(AuthorizationUrlOptions.none()),
                client.getAuthorizationUrl(AuthorizationUrlOptions.builder().build())
        );
    }
}

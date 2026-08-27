package com.nibub.globbookauth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private Config.Builder validBuilder() {
        return Config.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .redirectUrl("https://example.com/callback");
    }

    @Test
    void buildsWithDefaults() {
        Config config = validBuilder().build();
        assertEquals("client-123", config.getClientId());
        assertEquals("secret-abc", config.getClientSecret());
        assertEquals("https://example.com/callback", config.getRedirectUrl());
        assertEquals(Config.DEFAULT_BASE_URL, config.getBaseUrl());
        assertEquals(Config.DEFAULT_TIMEOUT, config.getTimeout());
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        Config config = validBuilder().baseUrl("https://staging.globbook.com/").build();
        assertEquals("https://staging.globbook.com", config.getBaseUrl());
    }

    @Test
    void customTimeoutIsStored() {
        Config config = validBuilder().timeout(Duration.ofSeconds(5)).build();
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
    }

    @Test
    void missingClientIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                Config.builder().clientSecret("secret").redirectUrl("https://example.com/cb").build());
    }

    @Test
    void blankClientSecretThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                Config.builder().clientId("id").clientSecret("   ").redirectUrl("https://example.com/cb").build());
    }

    @Test
    void missingRedirectUrlThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                Config.builder().clientId("id").clientSecret("secret").build());
    }

    @Test
    void toStringRedactsSecret() {
        Config config = validBuilder().build();
        String s = config.toString();
        assertFalse(s.contains("secret-abc"));
        assertTrue(s.contains("[REDACTED]"));
    }
}

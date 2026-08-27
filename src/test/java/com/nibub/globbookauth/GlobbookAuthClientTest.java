package com.nibub.globbookauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style tests against a real, local {@code HttpServer} — this
 * SDK is built on {@code java.net.http.HttpClient}, which has no simple
 * seam to inject a fake transport without a mocking library, so a
 * loopback server exercises the real request/response path instead.
 */
class GlobbookAuthClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpServer startServer(String path, int status, String responseBody) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext(path, exchange -> writeJson(exchange, status, responseBody));
        s.start();
        return s;
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private GlobbookAuthClient clientFor(HttpServer server) {
        Config config = Config.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .redirectUrl("https://example.com/callback")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        return new GlobbookAuthClient(config);
    }

    @Test
    void exchangeCodeForTokenSuccess() throws IOException {
        server = startServer("/api/v2/oauth/token", 200,
                "{\"access_token\":\"tok-xyz\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        GlobbookAuthClient client = clientFor(server);

        Token token = client.exchangeCodeForToken("auth-code-1");

        assertEquals("tok-xyz", token.accessToken());
        assertEquals("Bearer", token.tokenType());
        assertEquals(3600, token.expiresIn());
    }

    @Test
    void exchangeCodeForTokenInvalidGrant() throws IOException {
        server = startServer("/api/v2/oauth/token", 401,
                "{\"error\":\"invalid_grant\",\"error_description\":\"code expired\"}");
        GlobbookAuthClient client = clientFor(server);

        AuthError error = assertThrows(AuthError.class, () -> client.exchangeCodeForToken("stale-code"));

        assertEquals("invalid_grant", error.getCode());
        assertEquals("code expired", error.getDescription());
        assertEquals(401, error.getStatusCode());
    }

    @Test
    void exchangeCodeForTokenEmptyCodeThrowsWithoutRequest() {
        Config config = Config.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .redirectUrl("https://example.com/callback")
                .build();
        GlobbookAuthClient client = new GlobbookAuthClient(config);

        AuthError error = assertThrows(AuthError.class, () -> client.exchangeCodeForToken("   "));
        assertEquals(AuthError.Codes.INVALID_REQUEST, error.getCode());
    }

    @Test
    void getUserInfoSuccess() throws IOException {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v2/oauth/userinfo", exchange -> {
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200,
                    "{\"sub\":\"md5hash\",\"preferred_username\":\"janedoe\",\"profile_verified\":true,"
                            + "\"email\":\"jane@example.com\",\"name\":\"Jane Doe\",\"given_name\":\"Jane\","
                            + "\"family_name\":\"Doe\",\"bio\":\"\",\"picture\":null,\"cover_image\":null,\"website\":\"\"}");
        });
        server.start();
        GlobbookAuthClient client = clientFor(server);

        UserInfo info = client.getUserInfo("tok-xyz");

        assertEquals("Bearer tok-xyz", capturedAuthHeader.get());
        assertEquals("md5hash", info.getSub());
        assertEquals("jane@example.com", info.getEmail());
        assertNull(info.getPicture());
        assertTrue(info.isProfileVerified());
        assertNull(info.getBirthdate());
        assertNull(info.getGender());
        assertNull(info.getPhoneNumber());
        assertNull(info.getAddress());
    }

    @Test
    void getUserInfoRestrictedClaimsGranted() throws IOException {
        server = startServer("/api/v2/oauth/userinfo", 200,
                "{\"sub\":\"md5hash\",\"preferred_username\":\"janedoe\",\"profile_verified\":true,"
                        + "\"email\":\"jane@example.com\",\"name\":\"Jane Doe\",\"given_name\":\"Jane\","
                        + "\"family_name\":\"Doe\",\"bio\":\"\",\"picture\":null,\"cover_image\":null,\"website\":\"\","
                        + "\"birthdate\":\"1990-01-02\",\"gender\":\"female\",\"phone_number\":\"+15551234567\","
                        + "\"address\":\"Colombo Sri Lanka\"}");
        GlobbookAuthClient client = clientFor(server);

        UserInfo info = client.getUserInfo("tok-xyz");

        assertEquals("1990-01-02", info.getBirthdate());
        assertEquals("female", info.getGender());
        assertEquals("+15551234567", info.getPhoneNumber());
        assertEquals("Colombo Sri Lanka", info.getAddress());
    }

    @Test
    void getUserInfoInvalidToken() throws IOException {
        server = startServer("/api/v2/oauth/userinfo", 401,
                "{\"error\":\"invalid_token\",\"error_description\":\"token expired\"}");
        GlobbookAuthClient client = clientFor(server);

        AuthError error = assertThrows(AuthError.class, () -> client.getUserInfo("expired-token"));
        assertEquals("invalid_token", error.getCode());
    }

    @Test
    void getUserInfoEmptyAccessTokenThrowsWithoutRequest() {
        Config config = Config.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .redirectUrl("https://example.com/callback")
                .build();
        GlobbookAuthClient client = new GlobbookAuthClient(config);

        AuthError error = assertThrows(AuthError.class, () -> client.getUserInfo(""));
        assertEquals(AuthError.Codes.INVALID_REQUEST, error.getCode());
    }

    @Test
    void requestTimesOutWhenServerNeverResponds() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v2/oauth/token", exchange -> {
            // Deliberately never respond within the client's short timeout.
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, 200, "{}");
        });
        server.start();

        Config config = Config.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .redirectUrl("https://example.com/callback")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .timeout(Duration.ofMillis(200))
                .build();
        GlobbookAuthClient client = new GlobbookAuthClient(config);

        AuthError error = assertThrows(AuthError.class, () -> client.exchangeCodeForToken("some-code"));
        assertEquals(AuthError.Codes.TIMEOUT, error.getCode());
    }
}

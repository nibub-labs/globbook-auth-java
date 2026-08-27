import com.nibub.globbookauth.AuthError;
import com.nibub.globbookauth.AuthorizationUrlOptions;
import com.nibub.globbookauth.CallbackParams;
import com.nibub.globbookauth.Config;
import com.nibub.globbookauth.GlobbookAuthClient;
import com.nibub.globbookauth.Token;
import com.nibub.globbookauth.UserInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates a complete "Sign in with Globbook" flow: a "/login" handler
 * that redirects to Globbook's consent page, and a
 * "/auth/globbook/callback" handler that exchanges the returned code for
 * an access token and fetches the user's profile.
 *
 * <p>This is a demonstration, not a production auth system — it prints the
 * resulting profile instead of establishing a session, and stores the
 * pending CSRF state in an in-memory map keyed by a browser cookie rather
 * than a real session store.
 *
 * <p>Run it with:
 * <pre>
 * GLOBBOOK_CLIENT_ID=your-client-id \
 * GLOBBOOK_CLIENT_SECRET=your-client-secret \
 * GLOBBOOK_REDIRECT_URL=http://localhost:8080/auth/globbook/callback \
 * mvn -f example/pom.xml compile exec:java -Dexec.mainClass=ExampleApp
 * </pre>
 * Then open http://localhost:8080/login in a browser.
 */
public final class ExampleApp {

    // Demonstration only — a real app stores this server-side in a proper
    // session store, keyed by an actual session identifier.
    private static final Map<String, String> pendingState = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();

    public static void main(String[] args) throws IOException {
        GlobbookAuthClient client = new GlobbookAuthClient(Config.builder()
                .clientId(require("GLOBBOOK_CLIENT_ID"))
                .clientSecret(require("GLOBBOOK_CLIENT_SECRET"))
                .redirectUrl(require("GLOBBOOK_REDIRECT_URL"))
                // .baseUrl("https://staging.globbook.com") // uncomment to target staging
                .build());

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);

        server.createContext("/login", exchange -> {
            String state = randomHex();
            pendingState.put(state, state);
            String url = client.getAuthorizationUrl(AuthorizationUrlOptions.builder().state(state).build());
            exchange.getResponseHeaders().add("Location", url);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/auth/globbook/callback", exchange -> {
            CallbackParams params = GlobbookAuthClient.parseCallbackParams(exchange.getRequestURI().toString());
            if (params.code() == null || params.state() == null || pendingState.remove(params.state()) == null) {
                respond(exchange, 400, "Invalid or missing state -- possible CSRF.");
                return;
            }

            try {
                Token token = client.exchangeCodeForToken(params.code());
                UserInfo user = client.getUserInfo(token.accessToken());
                // A real app would look up or create a local account keyed
                // on user.getSub() and establish its own session here.
                String body = "<h1>Signed in with Globbook</h1><p>Welcome, " + escape(user.getName())
                        + " (@" + escape(user.getPreferredUsername()) + ")</p><p>" + escape(user.getEmail()) + "</p>";
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                respond(exchange, 200, body);
            } catch (AuthError e) {
                respond(exchange, 502, "sign-in failed: " + e.getCode());
            }
        });

        server.start();
        System.out.println("listening on http://localhost:8080 -- visit /login to start the flow");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String randomHex() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String require(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable: " + envVar);
        }
        return value;
    }
}

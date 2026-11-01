package bank.maggy.finance;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class IdentityClientTest {
    @Test
    void forwardsHeadersAndFailsClosedOnInvalidResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<>("{\"customerId\":\"" + UUID.randomUUID() + "\"}");
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/internal/identity/authenticate", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("Basic dTpw", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var client = new IdentityClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
        try {
            assertEquals(UUID.fromString(body.get().substring(15, 51)), client.authenticate("Basic dTpw"));
            body.set("{\"customerId\":\"invalid\"}");
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE, client);
            body.set("not json");
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE, client);
            status.set(401);
            assertStatus(HttpStatus.UNAUTHORIZED, client);
            status.set(403);
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE, client);
        } finally {
            server.stop(0);
        }
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, client);
    }

    private void assertStatus(HttpStatus expected, IdentityClient client) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, () -> client.authenticate("Basic dTpw")).getStatusCode());
    }
}

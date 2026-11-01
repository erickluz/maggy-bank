package bank.maggy.finance;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class IdentityClient {
    private final RestClient client;
    private final String internalKey;

    public IdentityClient(@Value("${maggy.identity-url:http://localhost:8081}") String identityUrl,
                          @Value("${maggy.internal-key}") String internalKey) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
        this.internalKey = internalKey;
    }

    record Authentication(String customerId) { }

    public UUID authenticate(String authorization) {
        try {
            Authentication result = client.get().uri("/internal/identity/authenticate")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
                    })
                    .onStatus(status -> status.value() != 200, (request, response) -> { throw unavailable(); })
                    .body(Authentication.class);
            if (result == null || result.customerId() == null ||
                    !result.customerId().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                throw unavailable();
            }
            return UUID.fromString(result.customerId());
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de identidade indisponível.");
    }
}

package bank.maggy.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TokenVerifier {
    private final byte[] secret;
    private final ObjectMapper mapper;

    public TokenVerifier(@Value("${maggy.jwt-secret}") String secret, ObjectMapper mapper) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET deve ter pelo menos 32 bytes.");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    public UUID customerId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) throw unauthorized();
        try {
            String[] parts = authorization.substring(7).split("\\.", -1);
            if (parts.length != 3) throw unauthorized();
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            if (!MessageDigest.isEqual(signature, mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII)))) throw unauthorized();
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())
                    || !"maggy-identity".equals(payload.path("iss").asText())
                    || payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) throw unauthorized();
            return UUID.fromString(payload.path("sub").asText());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unauthorized();
        }
    }

    private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido ou expirado."); }
}

package bank.maggy.identity.api;

import bank.maggy.identity.application.AuthenticationService;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAuthenticationController {
    private final AuthenticationService authentication;
    private final byte[] internalKey;

    public InternalAuthenticationController(AuthenticationService authentication, @Value("${maggy.internal-key:}") String internalKey) {
        this.authentication = authentication;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/internal/identity/authenticate")
    public AuthenticationResponse authenticate(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (internalKey.length == 0 || key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8)))
            throw AuthenticationService.unauthorized();
        if (authorization == null || authorization.length() <= 6 || !authorization.regionMatches(true, 0, "Basic ", 0, 6))
            throw AuthenticationService.unauthorized();
        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(6));
            String credentials = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(decoded)).toString();
            int separator = credentials.indexOf(':');
            if (separator < 1) throw AuthenticationService.unauthorized();
            return authentication.authenticate(credentials.substring(0, separator), credentials.substring(separator + 1));
        } catch (IllegalArgumentException | CharacterCodingException ex) {
            throw AuthenticationService.unauthorized();
        }
    }
}

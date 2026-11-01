package bank.maggy.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    record RegistrationResponse(UUID customerId, String displayName, String username, String accountStatus) { }
    record LoginResponse(String accessToken, String tokenType, long expiresIn) { }

    public RegistrationResponse register(String displayName, String username, String password) {
        throw new UnsupportedOperationException("TODO: implementar cadastro e abertura de conta");
    }

    public LoginResponse login(String username, String password) {
        throw new UnsupportedOperationException("TODO: implementar login");
    }

    public RegistrationResponse me(UUID id) {
        throw new UnsupportedOperationException("TODO: implementar consulta do cliente");
    }
}

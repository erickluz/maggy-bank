package bank.maggy.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    record RegistrationResponse(UUID customerId, String displayName, String username, String accountStatus) { }

    public RegistrationResponse register(String displayName, String username, String password) {
        throw new UnsupportedOperationException("TODO: implementar cadastro e abertura de conta");
    }

    public void login(String username, String password) {
        throw new UnsupportedOperationException("TODO: implementar login");
    }

    // TODO: definir como identificar o cliente antes de implementar a consulta da própria conta.
}

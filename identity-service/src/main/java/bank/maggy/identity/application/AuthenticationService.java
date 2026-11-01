package bank.maggy.identity.application;

import bank.maggy.identity.api.AuthenticationResponse;
import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticationService {
    private final CustomerRepository customers;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthenticationService(CustomerRepository customers) { this.customers = customers; }

    public AuthenticationResponse authenticate(String username, String password) {
        if (username == null || password == null || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw unauthorized();
        Customer customer = customers.findByUsername(username).orElseThrow(AuthenticationService::unauthorized);
        if (!passwords.matches(password, customer.passwordHash())) throw unauthorized();
        return new AuthenticationResponse(customer.id());
    }

    public static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
    }
}

package bank.maggy.identity;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);
    private final JdbcTemplate jdbc;
    private final RestClient finance;
    private final String internalKey;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final Tokens tokens;

    record Customer(UUID id, String displayName, String username, String hash, String status) { }
    record RegistrationResponse(UUID customerId, String displayName, String username, String accountStatus) { }
    record LoginResponse(String accessToken, String tokenType, long expiresIn) { }

    public IdentityService(JdbcTemplate jdbc, Tokens tokens, @Value("${maggy.finance-url}") String financeUrl,
                           @Value("${maggy.internal-key}") String internalKey) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.finance = RestClient.builder().baseUrl(financeUrl).requestFactory(requestFactory).build();
        this.internalKey = internalKey;
    }

    public RegistrationResponse register(String displayName, String username, String password) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 120
                || username == null || !username.matches("[a-zA-Z0-9._-]{3,60}")
                || password == null || password.length() < 8 || password.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, usuário ou senha inválidos.");
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO customer(id, display_name, username, password_hash, account_status) VALUES (?, ?, ?, ?, 'PENDING')",
                    id, displayName.trim(), username, passwords.encode(password));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nome de usuário já utilizado.");
        }
        // The pending row is committed before the remote request. Recovery can always retry it.
        return recover(id);
    }

    public LoginResponse login(String username, String password) {
        Customer customer = findByUsername(username);
        if (customer == null || password == null || !passwords.matches(password, customer.hash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        if ("PENDING".equals(customer.status())) recover(customer.id());
        return new LoginResponse(tokens.issue(customer.id()), "Bearer", tokens.expiresIn());
    }

    public RegistrationResponse me(UUID id) {
        Customer customer = findById(id);
        if (customer == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        if ("PENDING".equals(customer.status())) return recover(id);
        return response(customer);
    }

    @Scheduled(fixedDelayString = "${maggy.recovery-delay-ms:30000}")
    public void retryPending() {
        List<UUID> ids = jdbc.query("SELECT id FROM customer WHERE account_status = 'PENDING' ORDER BY created_at LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class));
        ids.forEach(this::recover);
    }

    private RegistrationResponse recover(UUID id) {
        Customer customer = findById(id);
        if (customer == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        if ("READY".equals(customer.status())) return response(customer);
        try {
            finance.post().uri("/internal/accounts").header("X-Internal-Key", internalKey)
                    .body(new OpenAccountRequest(customer.id(), customer.displayName())).retrieve().toBodilessEntity();
            jdbc.update("UPDATE customer SET account_status = 'READY' WHERE id = ?", id);
            return response(findById(id));
        } catch (Exception ex) {
            log.warn("Abertura de conta pendente para {}: {}", id, ex.toString());
            return response(customer);
        }
    }

    private Customer findByUsername(String username) {
        if (username == null) return null;
        try { return jdbc.queryForObject("SELECT * FROM customer WHERE username = ?", (rs, row) -> map(rs), username); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    private Customer findById(UUID id) {
        try { return jdbc.queryForObject("SELECT * FROM customer WHERE id = ?", (rs, row) -> map(rs), id); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    private Customer map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Customer(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("username"),
                rs.getString("password_hash"), rs.getString("account_status"));
    }

    private RegistrationResponse response(Customer c) { return new RegistrationResponse(c.id(), c.displayName(), c.username(), c.status()); }
    private record OpenAccountRequest(UUID customerId, String displayName) { }
}

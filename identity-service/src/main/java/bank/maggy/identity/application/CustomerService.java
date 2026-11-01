package bank.maggy.identity.application;

import bank.maggy.identity.api.CustomerRequest;
import bank.maggy.identity.api.CustomerResponse;
import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public CustomerService(CustomerRepository customers) { this.customers = customers; }

    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer(UUID.randomUUID(), request.displayName().trim(), request.username(),
                encode(request.password()), "READY");
        try { customers.insert(customer); }
        catch (DuplicateKeyException ex) { throw conflict(); }
        return response(customer);
    }

    public List<CustomerResponse> list() { return customers.findAll().stream().map(this::response).toList(); }

    public CustomerResponse get(UUID id) { return response(find(id)); }

    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer existing = find(id);
        Customer updated = new Customer(id, request.displayName().trim(), request.username(), encode(request.password()), existing.accountStatus());
        try {
            if (customers.update(updated) == 0) throw notFound();
        } catch (DuplicateKeyException ex) { throw conflict(); }
        return response(updated);
    }

    public void delete(UUID id) { if (customers.delete(id) == 0) throw notFound(); }

    private Customer find(UUID id) { return customers.findById(id).orElseThrow(this::notFound); }
    private CustomerResponse response(Customer customer) {
        return new CustomerResponse(customer.id(), customer.displayName(), customer.username(), customer.accountStatus());
    }
    private String encode(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter no máximo 72 bytes em UTF-8.");
        return passwords.encode(password);
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Nome de usuário já utilizado."); }
}

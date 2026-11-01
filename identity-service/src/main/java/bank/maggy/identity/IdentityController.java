package bank.maggy.identity;

import io.swagger.v3.oas.annotations.Operation;
import bank.maggy.identity.application.CustomerService;
import bank.maggy.identity.application.AuthenticationService;
import bank.maggy.identity.api.AuthenticationResponse;
import bank.maggy.identity.api.CustomerRequest;
import bank.maggy.identity.api.CustomerResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final CustomerService service;
    private final AuthenticationService authentication;
    public IdentityController(CustomerService service, AuthenticationService authentication) {
        this.service = service;
        this.authentication = authentication;
    }
    record LoginRequest(String username, String password) { }

    @PostMapping("/api/identity/register")
    @Operation(summary = "Cadastra cliente e inicia abertura da conta")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PostMapping("/api/identity/login")
    @Operation(summary = "Autentica cliente")
    public AuthenticationResponse login(@RequestBody LoginRequest body) {
        return authentication.authenticate(body.username(), body.password());
    }

    @GetMapping("/api/identity/me")
    @Operation(summary = "Consulta do próprio cliente (identificação pendente)")
    public CustomerResponse me() {
        throw new UnsupportedOperationException("TODO: definir identificação do cliente");
    }
}

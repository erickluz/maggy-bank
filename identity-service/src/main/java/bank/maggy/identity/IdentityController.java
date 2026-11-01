package bank.maggy.identity;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final IdentityService service;
    public IdentityController(IdentityService service) { this.service = service; }
    record RegisterRequest(String displayName, String username, String password) { }
    record LoginRequest(String username, String password) { }

    @PostMapping("/api/identity/register")
    @Operation(summary = "Cadastra cliente e inicia abertura da conta")
    public ResponseEntity<IdentityService.RegistrationResponse> register(@RequestBody RegisterRequest body) {
        IdentityService.RegistrationResponse response = service.register(body.displayName(), body.username(), body.password());
        return ResponseEntity.status("READY".equals(response.accountStatus()) ? 201 : 202).body(response);
    }

    @PostMapping("/api/identity/login")
    @Operation(summary = "Autentica cliente (pendente de implementação)")
    public void login(@RequestBody LoginRequest body) { service.login(body.username(), body.password()); }

    @GetMapping("/api/identity/me")
    @Operation(summary = "Consulta do próprio cliente (identificação pendente)")
    public IdentityService.RegistrationResponse me() {
        throw new UnsupportedOperationException("TODO: definir identificação do cliente");
    }
}

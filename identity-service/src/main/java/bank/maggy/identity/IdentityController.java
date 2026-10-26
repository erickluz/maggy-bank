package bank.maggy.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final IdentityService service;
    private final Tokens tokens;

    public IdentityController(IdentityService service, Tokens tokens) { this.service = service; this.tokens = tokens; }
    record RegisterRequest(String displayName, String username, String password) { }
    record LoginRequest(String username, String password) { }

    @PostMapping("/api/identity/register")
    public ResponseEntity<IdentityService.RegistrationResponse> register(@RequestBody RegisterRequest body) {
        IdentityService.RegistrationResponse response = service.register(body.displayName(), body.username(), body.password());
        return ResponseEntity.status("READY".equals(response.accountStatus()) ? 201 : 202).body(response);
    }

    @PostMapping("/api/identity/login")
    public IdentityService.LoginResponse login(@RequestBody LoginRequest body) { return service.login(body.username(), body.password()); }

    @GetMapping("/api/identity/me")
    public IdentityService.RegistrationResponse me(HttpServletRequest request) { return service.me(tokens.customerId(request)); }
}

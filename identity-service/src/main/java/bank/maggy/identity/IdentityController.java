package bank.maggy.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Cadastro e autenticação", description = "Cadastro e login de clientes fictícios. Ainda não há sessão ou token.")
public class IdentityController {
    private final CustomerService service;
    private final AuthenticationService authentication;
    public IdentityController(CustomerService service, AuthenticationService authentication) {
        this.service = service;
        this.authentication = authentication;
    }
    record LoginRequest(@Schema(description = "Nome de usuário informado no cadastro.", example = "maria.silva") String username,
                        @Schema(description = "Senha cadastrada; enviada somente para autenticação.", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) { }

    @PostMapping("/api/identity/register")
    @Operation(summary = "Cadastra cliente", description = "Cria um cadastro de cliente fictício com usuário único e senha protegida por hash. Atualmente retorna READY, mas não solicita nem confirma abertura de conta no financeiro.")
    @ApiResponse(responseCode = "201", description = "Cadastro criado; READY ainda não garante conta financeira.")
    @ApiResponse(responseCode = "400", description = "Dados ausentes ou inválidos.")
    @ApiResponse(responseCode = "409", description = "Nome de usuário já utilizado.")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PostMapping("/api/identity/login")
    @Operation(summary = "Autentica cliente", description = "Confere usuário e senha no serviço de identidade e retorna o identificador do cliente. Não cria sessão nem emite token.")
    @ApiResponse(responseCode = "200", description = "Credenciais válidas; retorna o identificador do cliente.")
    @ApiResponse(responseCode = "400", description = "JSON ausente ou inválido.")
    @ApiResponse(responseCode = "401", description = "Usuário ou senha inválidos.")
    public AuthenticationResponse login(@RequestBody LoginRequest body) {
        return authentication.authenticate(body.username(), body.password());
    }

    @GetMapping("/api/identity/me")
    @Operation(summary = "Consulta do próprio cliente", description = "Reservado para consultar o cliente atual. A identificação do chamador ainda não foi implementada.")
    @ApiResponse(responseCode = "501", description = "Operação ainda não implementada.")
    public CustomerResponse me() {
        throw new UnsupportedOperationException("TODO: definir identificação do cliente");
    }
}

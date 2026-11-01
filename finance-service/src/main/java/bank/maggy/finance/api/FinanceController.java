package bank.maggy.finance.api;

import bank.maggy.finance.FinanceAuthentication;
import bank.maggy.finance.application.FinanceService;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Financeiro", description = "Operações bancárias fictícias em centavos, com autenticação no serviço de identidade.")
@SecuritySchemes({
        @SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic", description = "Usuário e senha do cliente validados pelo serviço de identidade em cada requisição. Use HTTPS fora do ambiente local."),
        @SecurityScheme(name = "internalKey", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-Internal-Key", description = "Chave compartilhada, exclusiva para comunicação interna.")
})
public class FinanceController {
    private final FinanceService service;
    private final byte[] internalKey;

    public FinanceController(FinanceService service, @Value("${maggy.internal-key}") String internalKey) {
        this.service = service;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    record OpenAccountRequest(@Schema(description = "UUID do cliente cadastrado na identidade.") UUID customerId,
                              @Schema(description = "Nome fictício do titular da conta.", example = "Maria Silva") String displayName) { }

    @PostMapping("/internal/accounts")
    @Operation(summary = "Abre conta internamente", description = "Cria uma conta e crédito inicial uma única vez para o cliente; solicitações repetidas retornam a conta existente. Exige chave compartilhada e acesso interno.", security = @SecurityRequirement(name = "internalKey"))
    @ApiResponse(responseCode = "200", description = "Conta existente ou criada.")
    @ApiResponse(responseCode = "403", description = "Chave interna ausente ou inválida.")
    public FinanceService.Account open(@RequestBody OpenAccountRequest body, HttpServletRequest request) {
        String key = request.getHeader("X-Internal-Key");
        if (key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso interno negado.");
        return service.open(body.customerId(), body.displayName());
    }

    @GetMapping("/api/finance/account")
    @Operation(summary = "Consulta a própria conta", description = "Retorna número, titular e saldo da conta do cliente autenticado. O ID é obtido da identidade, não informado na requisição.", security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "200", description = "Conta e saldo.")
    @ApiResponse(responseCode = "401", description = "Credenciais ausentes ou inválidas.")
    @ApiResponse(responseCode = "503", description = "Identidade indisponível ou com resposta inválida.")
    public FinanceService.Account account(@Parameter(hidden = true) @RequestAttribute(FinanceAuthentication.CUSTOMER_ID) UUID customerId) { return service.accountByCustomer(customerId); }

    @GetMapping("/api/finance/account/statement")
    @Operation(summary = "Consulta o extrato", description = "Lista os lançamentos da conta do cliente autenticado, com valores em centavos e saldo após cada operação.", security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "200", description = "Lançamentos da conta.")
    @ApiResponse(responseCode = "401", description = "Credenciais ausentes ou inválidas.")
    @ApiResponse(responseCode = "503", description = "Identidade indisponível ou com resposta inválida.")
    public List<FinanceService.StatementEntry> statement(@Parameter(hidden = true) @RequestAttribute(FinanceAuthentication.CUSTOMER_ID) UUID customerId) { return service.statement(customerId); }

    @GetMapping("/api/finance/recipients/{number}")
    @Operation(summary = "Consulta destinatário", description = "Obtém o nome do titular a partir do número da conta de destino, antes da confirmação da transferência.", security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "200", description = "Número e nome do destinatário.")
    @ApiResponse(responseCode = "401", description = "Credenciais ausentes ou inválidas.")
    @ApiResponse(responseCode = "503", description = "Identidade indisponível ou com resposta inválida.")
    public FinanceService.Recipient recipient(@Parameter(description = "Número da conta financeira de destino; não é o UUID do cliente.", example = "100001") @PathVariable String number) {
        return service.recipient(number);
    }

    @PostMapping("/api/finance/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicita transferência", description = "Transfere centavos fictícios da conta autenticada para outra conta, usando requestId para identificar uma tentativa e evitar duplicação.", security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "201", description = "Comprovante da transferência.")
    @ApiResponse(responseCode = "401", description = "Credenciais ausentes ou inválidas.")
    @ApiResponse(responseCode = "503", description = "Identidade indisponível ou com resposta inválida.")
    public FinanceService.Receipt transfer(@Parameter(hidden = true) @RequestAttribute(FinanceAuthentication.CUSTOMER_ID) UUID customerId, @RequestBody FinanceService.TransferRequest body) {
        return service.transfer(customerId, body);
    }

    @GetMapping("/api/finance/transfers/{requestId}")
    @Operation(summary = "Consulta resultado de transferência", description = "Recupera o comprovante da tentativa pelo mesmo requestId enviado na criação.", security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "200", description = "Comprovante da tentativa.")
    @ApiResponse(responseCode = "400", description = "Identificador não é um UUID válido.")
    @ApiResponse(responseCode = "401", description = "Credenciais ausentes ou inválidas.")
    @ApiResponse(responseCode = "503", description = "Identidade indisponível ou com resposta inválida.")
    public FinanceService.Receipt receipt(@Parameter(hidden = true) @RequestAttribute(FinanceAuthentication.CUSTOMER_ID) UUID customerId, @Parameter(description = "UUID da tentativa informado em requestId ao solicitar a transferência.") @PathVariable UUID requestId) {
        return service.receipt(customerId, requestId);
    }
}

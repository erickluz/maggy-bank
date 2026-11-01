package bank.maggy.finance;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FinanceController {
    private final FinanceService service;
    private final byte[] internalKey;

    public FinanceController(FinanceService service, @Value("${maggy.internal-key}") String internalKey) {
        this.service = service;
        this.internalKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    record OpenAccountRequest(UUID customerId, String displayName) { }

    @PostMapping("/internal/accounts")
    public FinanceService.Account open(@RequestBody OpenAccountRequest body, HttpServletRequest request) {
        String key = request.getHeader("X-Internal-Key");
        if (key == null || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso interno negado.");
        return service.open(body.customerId(), body.displayName());
    }

    @GetMapping("/api/finance/account")
    public FinanceService.Account account() { throw unidentifiedCustomer(); }

    @GetMapping("/api/finance/account/statement")
    public List<FinanceService.StatementEntry> statement() { throw unidentifiedCustomer(); }

    @GetMapping("/api/finance/recipients/{number}")
    public FinanceService.Recipient recipient(@PathVariable String number) {
        throw unidentifiedCustomer();
    }

    @PostMapping("/api/finance/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceService.Receipt transfer(@RequestBody FinanceService.TransferRequest body) {
        throw unidentifiedCustomer();
    }

    @GetMapping("/api/finance/transfers/{requestId}")
    public FinanceService.Receipt receipt(@PathVariable UUID requestId) {
        throw unidentifiedCustomer();
    }

    private UnsupportedOperationException unidentifiedCustomer() {
        return new UnsupportedOperationException("TODO: definir identificação e autorização do cliente");
    }
}

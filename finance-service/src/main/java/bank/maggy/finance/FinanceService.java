package bank.maggy.finance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FinanceService {
    public record Account(UUID accountId, String accountNumber, UUID customerId, String displayName, long balanceCents, String currency) { }
    public record Recipient(String accountNumber, String displayName) { }
    public record StatementEntry(UUID operationId, String type, long amountCents, long balanceAfterCents, Instant occurredAt, String description) { }
    public record Receipt(UUID transferId, UUID requestId, String sourceAccountNumber, String sourceDisplayName,
                          String destinationAccountNumber, String destinationDisplayName, long amountCents, String currency, Instant completedAt) { }
    public record TransferRequest(UUID requestId, String destinationAccountNumber, long amountCents) { }

    public Account open(UUID customerId, String displayName) {
        throw new UnsupportedOperationException("TODO: implementar abertura de conta");
    }

    public Account accountByCustomer(UUID customerId) {
        throw new UnsupportedOperationException("TODO: implementar consulta de conta");
    }

    public Recipient recipient(String number) {
        throw new UnsupportedOperationException("TODO: implementar consulta de destinatário");
    }

    public List<StatementEntry> statement(UUID customerId) {
        throw new UnsupportedOperationException("TODO: implementar extrato");
    }

    public Receipt transfer(UUID customerId, TransferRequest request) {
        throw new UnsupportedOperationException("TODO: implementar transferência");
    }

    public Receipt receipt(UUID customerId, UUID requestId) {
        throw new UnsupportedOperationException("TODO: implementar comprovante");
    }
}

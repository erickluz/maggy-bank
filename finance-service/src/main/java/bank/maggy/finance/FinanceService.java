package bank.maggy.finance;

import bank.maggy.finance.domain.Money;
import bank.maggy.finance.infrastructure.FinanceStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinanceService {
    private final FinanceStore store;

    public FinanceService(FinanceStore store) { this.store = store; }

    public record Account(UUID accountId, String accountNumber, UUID customerId, String displayName, long balanceCents, String currency) { }
    public record Recipient(String accountNumber, String displayName) { }
    public record StatementEntry(UUID operationId, String type, long amountCents, long balanceAfterCents, Instant occurredAt, String description) { }
    public record Receipt(UUID transferId, UUID requestId, String sourceAccountNumber, String sourceDisplayName,
                   String destinationAccountNumber, String destinationDisplayName, long amountCents, String currency, Instant completedAt) { }
    public record TransferRequest(UUID requestId, String destinationAccountNumber, long amountCents) { }

    @Transactional
    public Account open(UUID customerId, String displayName) {
        if (customerId == null || displayName == null || displayName.isBlank() || displayName.length() > 120) throw badRequest("Dados da conta inválidos.");
        // Serialize retries for one customer, including a lost HTTP response after commit.
        store.lockCustomer(customerId);
        Account existing = store.accountByCustomer(customerId);
        if (existing != null) return existing;
        String number = store.nextAccountNumber();
        UUID id = UUID.randomUUID();
        store.insertAccount(id, customerId, number, displayName.trim());
        store.insertOpening(id);
        return accountByCustomer(customerId);
    }

    public Account accountByCustomer(UUID customerId) {
        Account account = store.accountByCustomer(customerId);
        if (account == null) throw notFound("Conta não encontrada.");
        return account;
    }

    public Recipient recipient(String number) {
        Recipient recipient = store.recipient(number);
        if (recipient == null) throw notFound("Destinatário não encontrado.");
        return recipient;
    }

    public List<StatementEntry> statement(UUID customerId) {
        Account account = accountByCustomer(customerId);
        return store.statement(account.accountId());
    }

    @Transactional
    public Receipt transfer(UUID customerId, TransferRequest request) {
        if (request == null || request.requestId() == null || request.destinationAccountNumber() == null || request.destinationAccountNumber().isBlank()
                || request.amountCents() <= 0) throw badRequest("Transferência inválida.");
        Account source = accountByCustomer(customerId);
        // Advisory lock makes concurrent retries of one request ID wait for the first result.
        store.lockRequest(request.requestId());
        Receipt existing = store.receipt(request.requestId());
        if (existing != null) {
            if (!existing.sourceAccountNumber().equals(source.accountNumber())
                    || !existing.destinationAccountNumber().equals(request.destinationAccountNumber())
                    || existing.amountCents() != request.amountCents()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tentativa reutilizada com outros dados.");
            return existing;
        }
        Account destination = store.accountByNumber(request.destinationAccountNumber());
        if (destination == null) throw notFound("Destinatário não encontrado.");
        if (source.accountId().equals(destination.accountId())) throw badRequest("A conta de destino deve ser diferente.");
        // All transfers acquire row locks in the same order, avoiding opposite-direction deadlocks.
        List<UUID> ids = List.of(source.accountId(), destination.accountId()).stream().sorted().toList();
        Account first = store.lockedAccount(ids.get(0));
        Account second = store.lockedAccount(ids.get(1));
        source = first.accountId().equals(source.accountId()) ? first : second;
        destination = first.accountId().equals(destination.accountId()) ? first : second;
        Money amount = new Money(request.amountCents());
        long sourceAfter = new Money(source.balanceCents()).debit(amount).cents();
        long destinationAfter = new Money(destination.balanceCents()).credit(amount).cents();
        Instant now = Instant.now();
        UUID transferId = UUID.randomUUID();
        store.updateBalance(source.accountId(), sourceAfter);
        store.updateBalance(destination.accountId(), destinationAfter);
        store.insertTransfer(transferId, request.requestId(), source, destination, amount.cents(), now);
        store.insertEntry(source.accountId(), transferId, "TRANSFER_OUT", -amount.cents(), sourceAfter, now, "Transferência para " + destination.displayName());
        store.insertEntry(destination.accountId(), transferId, "TRANSFER_IN", amount.cents(), destinationAfter, now, "Transferência de " + source.displayName());
        return store.receipt(request.requestId());
    }

    public Receipt receipt(UUID customerId, UUID requestId) {
        Account account = accountByCustomer(customerId);
        Receipt receipt = store.receipt(requestId);
        if (receipt == null || !receipt.sourceAccountNumber().equals(account.accountNumber())) throw notFound("Transferência não encontrada.");
        return receipt;
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}

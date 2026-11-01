package bank.maggy.finance.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import bank.maggy.finance.domain.Money;
import bank.maggy.finance.infrastructure.FinanceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinanceService {
    private static final long INITIAL_CREDIT = 100_000;
    private final FinanceStore store;

    public FinanceService(FinanceStore store) { this.store = store; }
    public record Account(
            @Schema(description = "UUID interno da conta.") UUID accountId,
            @Schema(description = "Número da conta usado para recebimento.", example = "100001") String accountNumber,
            @Schema(description = "UUID do cliente proprietário na identidade.") UUID customerId,
            @Schema(description = "Nome fictício do titular.", example = "Maria Silva") String displayName,
            @Schema(description = "Saldo em centavos inteiros; 100000 corresponde a R$ 1.000,00.", example = "100000") long balanceCents,
            @Schema(description = "Moeda da conta simulada.", example = "BRL") String currency) { }
    public record Recipient(@Schema(description = "Número da conta de destino.", example = "100002") String accountNumber,
                            @Schema(description = "Nome fictício do titular do destino.") String displayName) { }
    public record StatementEntry(@Schema(description = "Identificador do lançamento ou operação.") UUID operationId,
                                 @Schema(description = "Tipo do lançamento, como OPENING_CREDIT, TRANSFER_IN ou TRANSFER_OUT.") String type,
                                 @Schema(description = "Valor em centavos com sinal: positivo para entrada e negativo para saída.", example = "-1250") long amountCents,
                                 @Schema(description = "Saldo em centavos após o lançamento.") long balanceAfterCents,
                                 @Schema(description = "Data e hora da operação em UTC.") Instant occurredAt,
                                 @Schema(description = "Descrição legível do lançamento.") String description) { }
    public record Receipt(@Schema(description = "UUID da transferência concluída.") UUID transferId,
                          @Schema(description = "UUID escolhido para identificar a tentativa.") UUID requestId,
                          @Schema(description = "Número da conta remetente.") String sourceAccountNumber,
                          @Schema(description = "Nome do remetente no momento da transferência.") String sourceDisplayName,
                          @Schema(description = "Número da conta destinatária.") String destinationAccountNumber,
                          @Schema(description = "Nome do destinatário no momento da transferência.") String destinationDisplayName,
                          @Schema(description = "Valor transferido em centavos.", example = "1250") long amountCents,
                          @Schema(description = "Moeda simulada.", example = "BRL") String currency,
                          @Schema(description = "Data e hora de conclusão em UTC.") Instant completedAt) { }
    public record TransferRequest(@Schema(description = "UUID único da tentativa, reutilizado em uma repetição para prevenir duplicação.") UUID requestId,
                                  @Schema(description = "Número da conta que receberá o valor.", example = "100002") String destinationAccountNumber,
                                  @Schema(description = "Valor positivo em centavos inteiros; 1250 corresponde a R$ 12,50.", example = "1250") long amountCents) { }

    @Transactional
    public Account open(UUID customerId, String displayName) {
        if (customerId == null || displayName == null || displayName.isBlank() || displayName.length() > 120)
            throw badRequest("Dados da conta inválidos.");
        store.lockCustomer(customerId);
        Account existing = store.accountByCustomer(customerId);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        store.insertAccount(id, customerId, store.nextAccountNumber(), displayName.trim(), INITIAL_CREDIT);
        store.insertEntry(id, null, "OPENING_CREDIT", INITIAL_CREDIT, INITIAL_CREDIT, Instant.now(), "Crédito inicial simulado");
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
        return store.statement(accountByCustomer(customerId).accountId());
    }

    @Transactional
    public Receipt transfer(UUID customerId, TransferRequest request) {
        if (request == null || request.requestId() == null || request.destinationAccountNumber() == null
                || request.destinationAccountNumber().isBlank() || request.amountCents() <= 0)
            throw badRequest("Dados da transferência inválidos.");
        Account source = accountByCustomer(customerId);
        store.lockRequest(request.requestId());
        Receipt existing = store.receipt(request.requestId());
        if (existing != null) {
            if (!existing.sourceAccountNumber().equals(source.accountNumber())
                    || !existing.destinationAccountNumber().equals(request.destinationAccountNumber())
                    || existing.amountCents() != request.amountCents())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identificador de tentativa já utilizado com outros dados.");
            return existing;
        }
        Account destination = store.accountByNumber(request.destinationAccountNumber());
        if (destination == null) throw notFound("Destinatário não encontrado.");
        if (source.accountId().equals(destination.accountId())) throw badRequest("A conta de destino deve ser diferente da origem.");
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

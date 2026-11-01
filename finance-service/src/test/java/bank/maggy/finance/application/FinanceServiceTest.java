package bank.maggy.finance.application;

import bank.maggy.finance.infrastructure.FinanceStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceServiceTest {
    private final FinanceStore store = mock(FinanceStore.class);
    private final FinanceService service = new FinanceService(store);

    @Test
    void openingIsIdempotentAndDoesNotCreditTwice() {
        UUID customer = UUID.randomUUID();
        var existing = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 100000, "BRL");
        when(store.accountByCustomer(customer)).thenReturn(existing);
        assertEquals(existing, service.open(customer, "Alice"));
        verify(store).lockCustomer(customer);
        verify(store, never()).insertAccount(any(), any(), any(), any(), anyLong());
        verify(store, never()).insertEntry(any(), any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void transferRetryReturnsReceiptAndRejectsDifferentData() {
        UUID customer = UUID.randomUUID(), request = UUID.randomUUID();
        var account = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 100000, "BRL");
        var receipt = new FinanceService.Receipt(UUID.randomUUID(), request, "100001", "Alice", "100002", "Bruno", 500, "BRL", Instant.now());
        when(store.accountByCustomer(customer)).thenReturn(account);
        when(store.receipt(request)).thenReturn(receipt);
        assertEquals(receipt, service.transfer(customer, new FinanceService.TransferRequest(request, "100002", 500)));
        var error = assertThrows(ResponseStatusException.class, () -> service.transfer(customer, new FinanceService.TransferRequest(request, "100002", 501)));
        assertEquals(409, error.getStatusCode().value());
        verify(store, never()).updateBalance(any(), anyLong());
    }

    @Test
    void receiptIsOnlyVisibleToSender() {
        UUID customer = UUID.randomUUID(), request = UUID.randomUUID();
        when(store.accountByCustomer(customer)).thenReturn(new FinanceService.Account(UUID.randomUUID(), "100002", customer, "Bruno", 0, "BRL"));
        when(store.receipt(request)).thenReturn(new FinanceService.Receipt(UUID.randomUUID(), request, "100001", "Alice", "100002", "Bruno", 500, "BRL", Instant.now()));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.receipt(customer, request)).getStatusCode().value());
    }
}

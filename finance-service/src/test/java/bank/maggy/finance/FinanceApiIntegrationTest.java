package bank.maggy.finance;

import bank.maggy.finance.api.FinanceController;
import bank.maggy.finance.application.FinanceService;
import bank.maggy.finance.infrastructure.FinanceStore;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercita HTTP, autenticação e regras da aplicação, simulando apenas identidade e persistência. */
@WebMvcTest(FinanceController.class)
@Import({FinanceService.class, FinanceAuthentication.class, ApiErrors.class})
class FinanceApiIntegrationTest {
    private static final String AUTH = "Basic YWxpY2U6c2VuaGE=";
    @Autowired MockMvc mvc;
    @MockitoBean IdentityClient identity;
    @MockitoBean FinanceStore store;

    @Test
    void missingAccountReturnsNotFoundForAuthenticatedCustomer() throws Exception {
        UUID customer = UUID.randomUUID();
        when(identity.authenticate(AUTH)).thenReturn(customer);
        mvc.perform(get("/api/finance/account").header("Authorization", AUTH))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Conta não encontrada."));
    }

    @Test
    void invalidAmountIsRejectedBeforeLookingUpAccounts() throws Exception {
        when(identity.authenticate(AUTH)).thenReturn(UUID.randomUUID());
        String body = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"destinationAccountNumber\":\"100002\",\"amountCents\":0}";
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dados da transferência inválidos."));
        verifyNoInteractions(store);
    }

    @Test
    void insufficientFundsDoNotChangeBalances() throws Exception {
        UUID customer = UUID.randomUUID(), other = UUID.randomUUID();
        var source = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 100, "BRL");
        var destination = new FinanceService.Account(UUID.randomUUID(), "100002", other, "Bruno", 500, "BRL");
        when(identity.authenticate(AUTH)).thenReturn(customer);
        when(store.accountByCustomer(customer)).thenReturn(source);
        when(store.accountByNumber("100002")).thenReturn(destination);
        when(store.lockedAccount(source.accountId())).thenReturn(source);
        when(store.lockedAccount(destination.accountId())).thenReturn(destination);
        String body = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"destinationAccountNumber\":\"100002\",\"amountCents\":101}";
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Valor inválido ou saldo insuficiente."));
        verify(store, never()).updateBalance(any(), anyLong());
    }

    @Test
    void rejectsSelfTransferAndUnknownDestination() throws Exception {
        UUID customer = UUID.randomUUID();
        var source = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 1000, "BRL");
        when(identity.authenticate(AUTH)).thenReturn(customer);
        when(store.accountByCustomer(customer)).thenReturn(source);
        when(store.accountByNumber("100001")).thenReturn(source);
        String request = UUID.randomUUID().toString();
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + request + "\",\"destinationAccountNumber\":\"100001\",\"amountCents\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + request + "\",\"destinationAccountNumber\":\"999999\",\"amountCents\":100}"))
                .andExpect(status().isNotFound());
        verify(store, never()).updateBalance(any(), anyLong());
    }

    @Test
    void rejectsNegativeAmountAndMalformedRequests() throws Exception {
        when(identity.authenticate(AUTH)).thenReturn(UUID.randomUUID());
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"destinationAccountNumber\":\"100002\",\"amountCents\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/finance/transfers/nao-e-uuid").header("Authorization", AUTH)).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

    @Test
    void repeatedAttemptWithDifferentClientOrDataIsRejected() throws Exception {
        UUID customer = UUID.randomUUID(), requestId = UUID.randomUUID();
        var account = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 1000, "BRL");
        var original = new FinanceService.Receipt(UUID.randomUUID(), requestId, "100001", "Alice", "100002", "Bruno", 100, "BRL", Instant.now());
        when(identity.authenticate(AUTH)).thenReturn(customer);
        when(store.accountByCustomer(customer)).thenReturn(account);
        when(store.receipt(requestId)).thenReturn(original);
        String same = "{\"requestId\":\"" + requestId + "\",\"destinationAccountNumber\":\"100002\",\"amountCents\":100}";
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(same))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.transferId").value(original.transferId().toString()));
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(same.replace("100002", "100003")))
                .andExpect(status().isConflict());
        when(store.accountByCustomer(customer)).thenReturn(new FinanceService.Account(UUID.randomUUID(), "100003", customer, "Carla", 1000, "BRL"));
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(same))
                .andExpect(status().isConflict());
        verify(store, never()).updateBalance(any(), anyLong());
    }

    @Test
    void transferOfExactBalanceAndOverflowAtDestination() throws Exception {
        UUID customer = UUID.randomUUID(), other = UUID.randomUUID(), requestId = UUID.randomUUID();
        var source = new FinanceService.Account(UUID.randomUUID(), "100001", customer, "Alice", 100, "BRL");
        var destination = new FinanceService.Account(UUID.randomUUID(), "100002", other, "Bruno", 500, "BRL");
        var receipt = new FinanceService.Receipt(UUID.randomUUID(), requestId, "100001", "Alice", "100002", "Bruno", 100, "BRL", Instant.now());
        when(identity.authenticate(AUTH)).thenReturn(customer);
        when(store.accountByCustomer(customer)).thenReturn(source);
        when(store.accountByNumber("100002")).thenReturn(destination);
        when(store.lockedAccount(source.accountId())).thenReturn(source);
        when(store.lockedAccount(destination.accountId())).thenReturn(destination);
        when(store.receipt(requestId)).thenReturn(null, receipt);
        String body = "{\"requestId\":\"" + requestId + "\",\"destinationAccountNumber\":\"100002\",\"amountCents\":100}";
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amountCents").value(100));
        verify(store).updateBalance(source.accountId(), 0);
        verify(store).updateBalance(destination.accountId(), 600);
        reset(store);
        var full = new FinanceService.Account(destination.accountId(), "100002", other, "Bruno", Long.MAX_VALUE, "BRL");
        when(store.accountByCustomer(customer)).thenReturn(source);
        when(store.accountByNumber("100002")).thenReturn(full);
        when(store.lockedAccount(source.accountId())).thenReturn(source);
        when(store.lockedAccount(full.accountId())).thenReturn(full);
        mvc.perform(post("/api/finance/transfers").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(store, never()).updateBalance(any(), anyLong());
    }

    @Test
    void doesNotDiscloseReceiptToAnotherCustomer() throws Exception {
        UUID customer = UUID.randomUUID(), requestId = UUID.randomUUID();
        when(identity.authenticate(AUTH)).thenReturn(customer);
        when(store.accountByCustomer(customer)).thenReturn(new FinanceService.Account(UUID.randomUUID(), "100003", customer, "Carla", 0, "BRL"));
        when(store.receipt(requestId)).thenReturn(new FinanceService.Receipt(UUID.randomUUID(), requestId, "100001", "Alice", "100002", "Bruno", 100, "BRL", Instant.now()));
        mvc.perform(get("/api/finance/transfers/{id}", requestId).header("Authorization", AUTH))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Transferência não encontrada."));
    }
}

package bank.maggy.finance;

import bank.maggy.finance.api.FinanceController;
import bank.maggy.finance.application.FinanceService;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceController.class)
@Import(FinanceAuthentication.class)
class FinanceAuthenticationTest {
    @Autowired MockMvc mvc;
    @MockitoBean FinanceService service;
    @MockitoBean IdentityClient identity;
    private static final String BASIC = "Basic dXNlcjpwYXNz";

    @Test
    void rejectsMissingAndNonBasicCredentialsOnEveryRoute() throws Exception {
        for (String path : List.of("/api/finance/account", "/api/finance/account/statement", "/api/finance/recipients/123", "/api/finance/transfers/" + UUID.randomUUID())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/finance/transfers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/account").header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(identity, service);
    }

    @Test
    void propagatesAuthenticationFailuresWithoutCallingFinance() throws Exception {
        when(identity.authenticate(BASIC)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/finance/account").header("Authorization", BASIC)).andExpect(status().isUnauthorized());
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(identity).authenticate(BASIC);
        mvc.perform(get("/api/finance/recipients/123").header("Authorization", BASIC)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }

    @Test
    void delegatesUsingOnlyAuthenticatedCustomer() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        when(identity.authenticate(BASIC)).thenReturn(customer);
        mvc.perform(get("/api/finance/account").param("customerId", UUID.randomUUID().toString()).header("Authorization", BASIC)).andExpect(status().isOk());
        mvc.perform(get("/api/finance/account/statement").header("Authorization", BASIC)).andExpect(status().isOk());
        mvc.perform(get("/api/finance/recipients/123").header("Authorization", BASIC)).andExpect(status().isOk());
        mvc.perform(post("/api/finance/transfers").header("Authorization", BASIC).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + request + "\",\"destinationAccountNumber\":\"123\",\"amountCents\":10}")).andExpect(status().isCreated());
        mvc.perform(get("/api/finance/transfers/" + request).header("Authorization", BASIC)).andExpect(status().isOk());
        verify(identity, times(5)).authenticate(BASIC);
        verify(service).accountByCustomer(customer);
        verify(service).statement(customer);
        verify(service).recipient("123");
        verify(service).transfer(eq(customer), any());
        verify(service).receipt(customer, request);
    }

    @Test
    void internalRouteStillRequiresInternalKey() throws Exception {
        mvc.perform(post("/internal/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(identity, service);
    }
}

package bank.maggy.finance;

import bank.maggy.finance.infrastructure.FinanceStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requer PostgreSQL explicitamente configurado; cada chamada HTTP usa sua própria transação. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "maggy.internal-key=test-key")
@AutoConfigureMockMvc
class FinanceConcurrencyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean IdentityClient identity;
    @MockitoSpyBean FinanceStore store;
    private final List<UUID> customers = new ArrayList<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FINANCE_TEST_DB_URL"));
        if (System.getenv("FINANCE_TEST_DB_USER") != null)
            registry.add("spring.datasource.username", () -> System.getenv("FINANCE_TEST_DB_USER"));
        if (System.getenv("FINANCE_TEST_DB_PASSWORD") != null)
            registry.add("spring.datasource.password", () -> System.getenv("FINANCE_TEST_DB_PASSWORD"));
    }

    @AfterEach
    void cleanup() {
        // Remove somente contas deste teste, primeiro os dependentes, respeitando as FKs.
        for (UUID customer : customers) {
            jdbc.update("DELETE FROM statement_entry WHERE account_id IN (SELECT id FROM account WHERE customer_id = ?)", customer);
        }
        for (UUID customer : customers) {
            jdbc.update("DELETE FROM transfer WHERE source_account_id IN (SELECT id FROM account WHERE customer_id = ?) AND destination_account_id IN (SELECT id FROM account WHERE customer_id IN (" + placeholders() + "))", args(customer));
        }
        for (UUID customer : customers) jdbc.update("DELETE FROM account WHERE customer_id = ?", customer);
    }

    private String placeholders() { return String.join(",", java.util.Collections.nCopies(customers.size(), "?")); }
    private Object[] args(UUID customer) {
        List<UUID> values = new ArrayList<>();
        values.add(customer);
        values.addAll(customers);
        return values.toArray();
    }

    @Test
    void competingTransfersCannotOverspend() throws Exception {
        Account source = open(), first = open(), second = open();
        List<MvcResult> results = concurrently(
                () -> transfer(source, first, UUID.randomUUID(), 70000),
                () -> transfer(source, second, UUID.randomUUID(), 70000));
        assertEquals(List.of(201, 400), results.stream().map(r -> r.getResponse().getStatus()).sorted().toList());
        assertEquals(30000, balance(source));
        assertEquals(300000, balance(source) + balance(first) + balance(second));
        assertEquals(1, transfers(source));
        assertEquals(2, entries(source));
        assertEquals(3, entries(first) + entries(second));
    }

    @Test
    void simultaneousIdenticalRequestsProduceOneTransfer() throws Exception {
        Account source = open(), destination = open();
        UUID request = UUID.randomUUID();
        List<MvcResult> results = concurrently(() -> transfer(source, destination, request, 1250),
                () -> transfer(source, destination, request, 1250));
        assertEquals(201, results.get(0).getResponse().getStatus());
        assertEquals(201, results.get(1).getResponse().getStatus());
        assertEquals(json.readTree(results.get(0).getResponse().getContentAsString()), json.readTree(results.get(1).getResponse().getContentAsString()));
        assertEquals(98750, balance(source));
        assertEquals(101250, balance(destination));
        assertEquals(1, transfers(source));
        assertEquals(2, entries(source));
        assertEquals(2, entries(destination));
    }

    @Test
    void oppositeTransfersCompleteWithoutDeadlock() throws Exception {
        Account first = open(), second = open();
        List<MvcResult> results = concurrently(() -> transfer(first, second, UUID.randomUUID(), 1000),
                () -> transfer(second, first, UUID.randomUUID(), 2000));
        assertEquals(List.of(201, 201), results.stream().map(r -> r.getResponse().getStatus()).toList());
        assertEquals(101000, balance(first));
        assertEquals(99000, balance(second));
        assertEquals(1, transfers(first));
        assertEquals(1, transfers(second));
        assertEquals(3, entries(first));
        assertEquals(3, entries(second));
    }

    @Test
    void failureAfterWritesRollsBackEverything() throws Exception {
        Account source = open(), destination = open();
        UUID request = UUID.randomUUID();
        // Falha localizada após o primeiro lançamento real, sem alterar esquema ou dados alheios.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (source.id().equals(invocation.getArgument(0)) && "TRANSFER_OUT".equals(invocation.getArgument(2)))
                throw new IllegalStateException("falha induzida");
            return result;
        }).when(store).insertEntry(any(), any(), anyString(), anyLong(), anyLong(), any(), anyString());
        assertThrows(Exception.class, () -> transfer(source, destination, request, 1250));
        assertEquals(100000, balance(source));
        assertEquals(100000, balance(destination));
        assertEquals(0, transfers(source));
        assertEquals(1, entries(source));
        assertEquals(1, entries(destination));
    }

    private record Account(UUID id, String number, String auth) { }

    private Account open() throws Exception {
        UUID customer = UUID.randomUUID();
        customers.add(customer);
        String auth = "Basic " + java.util.Base64.getEncoder().encodeToString((customer + ":senha").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(identity.authenticate(auth)).thenReturn(customer);
        String body = mvc.perform(post("/internal/accounts").header("X-Internal-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":\"" + customer + "\",\"displayName\":\"Teste\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode account = json.readTree(body);
        return new Account(UUID.fromString(account.path("accountId").asText()), account.path("accountNumber").asText(), auth);
    }

    private MvcResult transfer(Account source, Account destination, UUID request, long amount) throws Exception {
        return mvc.perform(post("/api/finance/transfers").header("Authorization", source.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"" + request + "\",\"destinationAccountNumber\":\"" + destination.number() + "\",\"amountCents\":" + amount + "}")) .andReturn();
    }

    private long balance(Account account) {
        return jdbc.queryForObject("SELECT balance_cents FROM account WHERE id = ?", Long.class, account.id());
    }
    private int transfers(Account account) {
        return jdbc.queryForObject("SELECT count(*) FROM transfer WHERE source_account_id = ?", Integer.class, account.id());
    }
    private int entries(Account account) {
        return jdbc.queryForObject("SELECT count(*) FROM statement_entry WHERE account_id = ?", Integer.class, account.id());
    }

    @SafeVarargs
    private final List<MvcResult> concurrently(Callable<MvcResult>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length), start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("início expirou");
                return call.call();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "requisições não terminaram");
        }
    }
}

package bank.maggy.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Execute somente com um banco PostgreSQL dedicado a testes. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = "maggy.internal-key=test-key")
@AutoConfigureMockMvc
@Transactional
class FinanceDatabaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean IdentityClient identity;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FINANCE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("FINANCE_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("FINANCE_TEST_DB_PASSWORD"));
    }

    @Test
    void opensAccountsAndTransfersIdempotentlyThroughRest() throws Exception {
        UUID sender = UUID.randomUUID(), receiver = UUID.randomUUID(), requestId = UUID.randomUUID();
        String senderAuth = "Basic c2VuZGVyOnBhc3M=", receiverAuth = "Basic cmVjZWl2ZXI6cGFzcw==";
        when(identity.authenticate(senderAuth)).thenReturn(sender);
        when(identity.authenticate(receiverAuth)).thenReturn(receiver);
        JsonNode first = open(sender, "Alice");
        JsonNode second = open(receiver, "Bruno");
        mvc.perform(post("/internal/accounts").header("X-Internal-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + sender + "\",\"displayName\":\"Alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(first.path("accountId").asText()));
        mvc.perform(get("/api/finance/recipients/{number}", second.path("accountNumber").asText()).header("Authorization", senderAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Bruno"));
        String transfer = "{\"requestId\":\"" + requestId + "\",\"destinationAccountNumber\":\"" + second.path("accountNumber").asText() + "\",\"amountCents\":1250}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/finance/transfers").header("Authorization", senderAuth).contentType(MediaType.APPLICATION_JSON).content(transfer))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amountCents").value(1250));
        mvc.perform(get("/api/finance/account").header("Authorization", senderAuth)).andExpect(status().isOk()).andExpect(jsonPath("$.balanceCents").value(98750));
        mvc.perform(get("/api/finance/account").header("Authorization", receiverAuth)).andExpect(status().isOk()).andExpect(jsonPath("$.balanceCents").value(101250));
        mvc.perform(get("/api/finance/account/statement").header("Authorization", senderAuth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/finance/transfers/{id}", requestId).header("Authorization", senderAuth)).andExpect(status().isOk());
        mvc.perform(get("/api/finance/transfers/{id}", requestId).header("Authorization", receiverAuth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/finance/transfers").header("Authorization", senderAuth).contentType(MediaType.APPLICATION_JSON).content(transfer.replace("1250", "1251")))
                .andExpect(status().isConflict());
    }

    private JsonNode open(UUID id, String name) throws Exception {
        String body = mvc.perform(post("/internal/accounts").header("X-Internal-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + id + "\",\"displayName\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balanceCents").value(100000)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}

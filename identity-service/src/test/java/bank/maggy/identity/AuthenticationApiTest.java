package bank.maggy.identity;

import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration", "maggy.internal-key=test-key"})
@AutoConfigureMockMvc
class AuthenticationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerRepository customers;

    private String basic(String value) {
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void authenticatesWithBothEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        when(customers.findByUsername("pessoa")).thenReturn(Optional.of(new Customer(id, "Pessoa", "pessoa", new BCryptPasswordEncoder().encode("senha:123"), "READY")));
        mvc.perform(post("/api/identity/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"pessoa\",\"password\":\"senha:123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(id.toString()));
        mvc.perform(get("/internal/identity/authenticate").header("X-Internal-Key", "test-key").header("Authorization", basic("pessoa:senha:123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(id.toString()));
    }

    @Test
    void rejectsWrongCredentialsGenerically() throws Exception {
        when(customers.findByUsername("pessoa")).thenReturn(Optional.of(new Customer(UUID.randomUUID(), "Pessoa", "pessoa", new BCryptPasswordEncoder().encode("senha123"), "READY")));
        for (String body : new String[]{"{\"username\":\"pessoa\",\"password\":\"errada\"}", "{\"username\":\"ausente\",\"password\":\"errada\"}", "{}"}) {
            mvc.perform(post("/api/identity/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciais inválidas."));
        }
        mvc.perform(get("/internal/identity/authenticate").header("X-Internal-Key", "test-key").header("Authorization", basic("pessoa:errada")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciais inválidas."));
    }

    @Test
    void rejectsInvalidBasicAndInternalKeys() throws Exception {
        for (String header : new String[]{"Bearer abc", "Basic !!!", basic("sem-separador"), basic(":senha"), "Basic "}) {
            mvc.perform(get("/internal/identity/authenticate").header("X-Internal-Key", "test-key").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/internal/identity/authenticate").header("X-Internal-Key", "test-key")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/identity/authenticate").header("Authorization", basic("pessoa:senha"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/identity/authenticate").header("X-Internal-Key", "errada").header("Authorization", basic("pessoa:senha"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(customers);
    }
}

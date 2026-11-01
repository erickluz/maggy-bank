package bank.maggy.identity;

import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration", "maggy.internal-key=test-key"})
@AutoConfigureMockMvc
class IdentityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerRepository customers;

    private String body(String name, String username, String password) {
        return "{\"displayName\":\"" + name + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void validatesBoundariesAndAcceptsExactly72Utf8Bytes() throws Exception {
        for (String invalid : new String[]{body(" ", "abc", "12345678"), body("x".repeat(121), "abc", "12345678"),
                body("Nome", "ab", "12345678"), body("Nome", "a".repeat(61), "12345678"),
                body("Nome", "abc!", "12345678"), body("Nome", "abc", "1234567"),
                body("Nome", "abc", "a".repeat(73))}) {
            mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(customers);
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("x".repeat(120), "a".repeat(60), "á".repeat(36))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).insert(saved.capture());
        assertTrue(new BCryptPasswordEncoder().matches("á".repeat(36), saved.getValue().passwordHash()));
    }

    @Test
    void updateReplacesPasswordAndPreservesStatusWithoutLeakingSecrets() throws Exception {
        UUID id = UUID.randomUUID();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String oldHash = encoder.encode("senha-antiga");
        when(customers.findById(id)).thenReturn(Optional.of(new Customer(id, "Anterior", "pessoa", oldHash, "PENDING")));
        when(customers.update(any())).thenReturn(1);
        mvc.perform(put("/api/identity/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(body(" Novo ", "pessoa", "senha-nova")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(content().string(not(containsString("senha-nova"))))
                .andExpect(content().string(not(containsString(oldHash))))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).update(saved.capture());
        assertEquals("Novo", saved.getValue().displayName());
        assertTrue(encoder.matches("senha-nova", saved.getValue().passwordHash()));
        assertFalse(encoder.matches("senha-antiga", saved.getValue().passwordHash()));
        when(customers.findByUsername("pessoa")).thenReturn(Optional.of(saved.getValue()));
        mvc.perform(post("/api/identity/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"pessoa\",\"password\":\"senha-antiga\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/identity/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"pessoa\",\"password\":\"senha-nova\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}

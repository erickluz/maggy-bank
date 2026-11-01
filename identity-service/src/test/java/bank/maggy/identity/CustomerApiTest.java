package bank.maggy.identity;

import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
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

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@AutoConfigureMockMvc
class CustomerApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerRepository customers;
    private static final String BODY = """
            {"displayName":" Pessoa ","username":"pessoa","password":"senha123"}
            """;

    @Test
    void registerStoresHashedPasswordAndReturnsReady() throws Exception {
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountStatus").value("READY"))
                .andExpect(jsonPath("$.displayName").value("Pessoa")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var captor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).insert(captor.capture());
        assertEquals("READY", captor.getValue().accountStatus());
        assertTrue(new BCryptPasswordEncoder().matches("senha123", captor.getValue().passwordHash()));
    }

    @Test
    void validatesAndRejectsDuplicateUsername() throws Exception {
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Verifique os campos informados."))
                .andExpect(jsonPath("$.errors[*].field", hasItems("displayName", "username", "password")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Informe a senha.")));
        doThrow(new DuplicateKeyException("duplicate")).when(customers).insert(any());
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Nome de usuário já utilizado."));
    }

    @Test
    void listsReadsUpdatesAndDeletes() throws Exception {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "Antigo", "antigo", "hash", "PENDING");
        when(customers.findAll()).thenReturn(List.of(customer));
        when(customers.findById(id)).thenReturn(Optional.of(customer));
        when(customers.update(any())).thenReturn(1);
        when(customers.delete(id)).thenReturn(1);
        mvc.perform(get("/api/identity/customers")).andExpect(status().isOk()).andExpect(jsonPath("$[0].customerId").value(id.toString()));
        mvc.perform(get("/api/identity/customers/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("antigo"));
        mvc.perform(put("/api/identity/customers/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("pessoa"));
        mvc.perform(delete("/api/identity/customers/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void missingCustomerReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/identity/customers/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cliente não encontrado."));
        mvc.perform(put("/api/identity/customers/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/identity/customers/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void reportsMalformedRequestsAndUnimplementedEndpoints() throws Exception {
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Corpo da requisição ausente ou JSON inválido."));
        mvc.perform(get("/api/identity/customers/invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Parâmetro inválido: id."));
        mvc.perform(get("/api/identity/me")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.message").value("Esta funcionalidade ainda não foi implementada."));
        mvc.perform(get("/api/identity/inexistente")).andExpect(status().isNotFound());
    }

    @Test
    void hidesInternalErrorDetails() throws Exception {
        when(customers.findAll()).thenThrow(new IllegalStateException("segredo interno"));
        mvc.perform(get("/api/identity/customers")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocorreu um erro interno. Tente novamente mais tarde."))
                .andExpect(content().string(not(containsString("segredo interno"))));
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimitWithoutReturningIt() throws Exception {
        String password = "á".repeat(37);
        String body = "{\"displayName\":\"Pessoa\",\"username\":\"pessoa\",\"password\":\"" + password + "\"}";
        mvc.perform(post("/api/identity/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha deve ter no máximo 72 bytes em UTF-8."))
                .andExpect(content().string(not(containsString(password))));
        verifyNoInteractions(customers);
    }

    @Test
    void rejectsDuplicateUsernameOnUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.of(new Customer(id, "Original", "original", "hash", "READY")));
        when(customers.update(any())).thenThrow(new DuplicateKeyException("duplicate"));
        mvc.perform(put("/api/identity/customers/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nome de usuário já utilizado."));
    }
}

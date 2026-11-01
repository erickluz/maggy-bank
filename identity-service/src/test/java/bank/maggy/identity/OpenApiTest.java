package bank.maggy.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import bank.maggy.identity.infrastructure.CustomerRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerRepository customers;

    @Test
    void exposesIdentityEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/identity/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/identity/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/identity/customers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/identity/login'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/identity/authenticate'].get").exists())
                .andExpect(jsonPath("$.paths['/api/identity/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/identity/register'].post.description", containsString("não solicita")))
                .andExpect(jsonPath("$.paths['/api/identity/customers/{id}'].delete.description", containsString("financeiro")))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.password.description", containsString("72 bytes")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

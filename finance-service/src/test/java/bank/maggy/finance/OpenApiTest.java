package bank.maggy.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import bank.maggy.finance.infrastructure.FinanceStore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean FinanceStore store;

    @Test
    void exposesFinanceEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/finance/account'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/account/statement'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/recipients/{number}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/transfers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/finance/transfers/{requestId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/account'].get.description", containsString("autenticado")))
                .andExpect(jsonPath("$.paths['/api/finance/account'].get.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/finance/account'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.components.schemas.TransferRequest.properties.amountCents.description", containsString("centavos")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

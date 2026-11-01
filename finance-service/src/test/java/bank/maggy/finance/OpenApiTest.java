package bank.maggy.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesFinanceEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/finance/account'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/account/statement'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/recipients/{number}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/finance/transfers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/finance/transfers/{requestId}'].get").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}

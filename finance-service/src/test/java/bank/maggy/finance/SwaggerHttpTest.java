package bank.maggy.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import bank.maggy.finance.infrastructure.FinanceStore;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class SwaggerHttpTest {
    @Autowired TestRestTemplate http;
    @MockitoBean FinanceStore store;

    @Test
    void servesSwaggerAndOpenApiOnApplicationPort() {
        var ui = http.getForEntity("/swagger-ui/index.html", String.class);
        var config = http.getForEntity("/v3/api-docs/swagger-config", String.class);
        var docs = http.getForEntity("/v3/api-docs", String.class);

        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).contains("Swagger UI");
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config.getBody()).contains("/v3/api-docs");
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("/api/finance/account");
    }
}

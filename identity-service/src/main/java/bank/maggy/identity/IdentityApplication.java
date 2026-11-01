package bank.maggy.identity;

import org.springframework.boot.SpringApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Maggy Bank — Identidade", version = "0.1.0", description = "API de estudo para cadastro e autenticação de clientes fictícios. O CRUD não possui autorização; não utilizar em produção."))
@EnableScheduling
public class IdentityApplication {
    public static void main(String[] args) { SpringApplication.run(IdentityApplication.class, args); }
}

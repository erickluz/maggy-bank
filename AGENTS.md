# Contexto para agentes

## Estado atual e fontes

- Projeto de estudo com dinheiro fictício. Consulte `docs/primeira-iteracao.md` para escopo, mas confirme o comportamento no código: a especificação ainda descreve token e abertura automática que não existem hoje.
- `contracts/*.yaml` são contratos REST mantidos manualmente; o Swagger/OpenAPI em execução vem das anotações Java. Ao alterar uma API, confira ambos e seus consumidores; o contrato de cadastro ainda descreve estados de abertura não implementados.
- O login retorna `customerId`, sem token ou sessão. O financeiro exige HTTP Basic em cada chamada pública e consulta `/internal/identity/authenticate` via `IdentityClient`, enviando também `X-Internal-Key`.
- Cadastro retorna `READY`, mas não cria conta. A conta precisa ser aberta em `POST /internal/accounts` com `customerId`, `displayName` e `X-Internal-Key`; a abertura é idempotente e credita 100000 centavos uma única vez. `FINANCE_URL` configurada não significa integração implementada.
- `/api/identity/me` não está implementado. O CRUD de clientes não tem autenticação/autorização; excluir cliente só remove identidade, não a conta financeira.
- `web/src/app.component.ts` é uma estrutura standalone com métodos de jornada que lançam TODO; o campo `token` remanescente não representa autenticação funcional.

## Fronteiras e invariantes

- Dois projetos Maven independentes, Java 21/Spring Boot, sem POM raiz nem Maven Wrapper. Cada serviço é dono de seu banco PostgreSQL e de `src/main/resources/db/migration/`; persistência usa `JdbcTemplate`, não JPA.
- No financeiro, `api/FinanceController` chama `application/FinanceService`, que concentra as transações e usa `infrastructure/FinanceStore`. Valores são centavos inteiros (`domain/Money`), não valores decimais em reais.
- Preserve a atomicidade entre saldos, transferência e extratos. A concorrência depende de locks PostgreSQL: advisory locks por cliente/tentativa e `FOR UPDATE` nas duas contas em ordem de UUID. A mesma `requestId` com outros dados retorna conflito; consultar comprovante usa `requestId`, não `transferId`.

## Comandos e verificação

Execute na raiz, com JDK 21 e Maven instalados:

```sh
mvn -f identity-service/pom.xml test
mvn -f finance-service/pom.xml test
mvn -f finance-service/pom.xml "-Dtest=FinanceServiceTest" test
```

- Para um método específico: `"-Dtest=FinanceServiceTest#nomeDoMetodo"`. Os testes rodam pelo Surefire em `test`, inclusive os opcionais de integração; não há etapa separada de Failsafe.
- Em `web/`: `npm ci` e `npm run build`; `npm start` inicia o dev server, mas não há proxy de API configurado nele. O Docker usa Node 24 e serve `dist/maggy-bank/browser`. Não há tarefas de teste, lint ou formatter configuradas para o frontend.
- Testes financeiros com PostgreSQL exigem banco exclusivo e `FINANCE_TEST_DB_URL`, `FINANCE_TEST_DB_USER`, `FINANCE_TEST_DB_PASSWORD`; aplicam Flyway e simulam autenticação de identidade. Sem a URL, são ignorados.
- `IdentityPostgresTest` exige as três variáveis `IDENTITY_TEST_DB_URL`, `IDENTITY_TEST_DB_USER`, `IDENTITY_TEST_DB_PASSWORD` e a migration de identidade **já aplicada**: esse teste desativa Flyway. Sem qualquer uma delas, é ignorado. Uma suíte verde sem essas variáveis não verifica persistência/concorrência reais.
- `infra/smoke.ps1` ainda exige Bearer token, `/me` e conta automática; não é uma verificação funcional do sistema atual.

## Ambiente local

- `docker compose up -d --build` sobe a aplicação em `http://localhost`. Somente o proxy publica porta (80); serviços (8081/8082) e PostgreSQL não ficam acessíveis diretamente pelo host. Para rodar serviços/testes fora do Docker, providencie PostgreSQL acessível e configure as variáveis de banco.
- O proxy bloqueia `/internal/`; execute chamadas internas pela rede Docker ou por um serviço iniciado diretamente. Swagger da identidade: `/swagger-ui/index.html`; financeiro: `/finance-docs/swagger-ui/index.html`.
- `INTERNAL_API_KEY` deve coincidir nos dois serviços (`.env.example`). Credenciais dos bancos locais estão em `compose.yaml` e `infra/postgres/init.sql`; o SQL de inicialização só roda na criação do volume. Alterar variáveis não altera usuários/senhas de um volume existente. `docker compose down` preserva `postgres_data`.

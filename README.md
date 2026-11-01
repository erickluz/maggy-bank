# Maggy Bank

Banco digital fictício para estudo de engenharia de software. Nenhuma operação movimenta dinheiro real.

Este repositório é um projeto de estudo: cadastro, autenticação e operações financeiras simuladas estão implementados. A abertura automática da conta no cadastro e os fluxos da interface ainda estão pendentes. Não use esta aplicação como banco real.

**Segurança:** as rotas de CRUD de clientes não possuem autenticação ou autorização e devem ser usadas apenas em ambiente local de estudo. A exclusão remove somente o cadastro de identidade, sem excluir ou alterar contas financeiras.

O serviço de identidade retorna erros em JSON com `timestamp`, `status`, `message`, `path` e `errors`. Em erros de validação, `errors` contém os campos e suas mensagens; nos demais casos, é uma lista vazia. Valores enviados, incluindo senhas, não são incluídos nas respostas de erro.

A primeira iteração está especificada em [docs/primeira-iteracao.md](docs/primeira-iteracao.md). Os contratos REST ficam em `contracts/`.

## Executar localmente

Requisitos: Docker com Compose. Na raiz do repositório:

```sh
docker compose up -d --build
```

Acesse `http://localhost` para visualizar a estrutura da interface. As jornadas só funcionarão após a implementação das funcionalidades descritas na especificação.

A documentação REST gerada do serviço de identidade está em `http://localhost/swagger-ui/index.html` (OpenAPI em `http://localhost/v3/api-docs`). Ao executar o serviço diretamente, use a porta `8081`. O login recebe `username` e `password` e retorna o `customerId`; não emite token.

A documentação do serviço financeiro, executado diretamente, fica na mesma porta da aplicação: Swagger UI em `http://localhost:8082/swagger-ui/index.html` e OpenAPI em `http://localhost:8082/v3/api-docs`. A porta padrão é configurada por `server.port` em `finance-service/src/main/resources/application.yml`. Pelo proxy do Compose, os endereços são `http://localhost/finance-docs/swagger-ui/index.html` e `http://localhost/finance-docs/v3/api-docs`. As rotas públicas financeiras exigem HTTP Basic: envie o usuário e a senha em cada requisição. O financeiro valida as credenciais no serviço de identidade; `IDENTITY_URL` configura o endereço interno.

A conta é aberta pela API interna `POST /internal/accounts`, com `X-Internal-Key`, `customerId` e `displayName`. A abertura é idempotente e credita R$ 1.000,00 fictícios uma única vez. O cadastro na identidade **ainda não chama essa API**: até integrar a abertura automática, um serviço autorizado deve abri-la antes da consulta de saldo ou transferência. O `READY` atual do cadastro não garante que a conta financeira exista.

Para testar a integração REST com PostgreSQL, configure `FINANCE_TEST_DB_URL`, `FINANCE_TEST_DB_USER` e `FINANCE_TEST_DB_PASSWORD` para um banco exclusivo de teste e execute `mvn test` em `finance-service/`. O teste de integração aplica migrations e reverte os dados da jornada; sem a URL ele é ignorado. A autenticação da identidade é simulada nesse teste.

HTTP Basic não criptografa credenciais. O Compose usa HTTP apenas para estudo local; fora desse ambiente é indispensável HTTPS, inclusive entre serviços, além de autenticação e proteção adequadas. O CRUD de clientes ainda não está protegido e a interface web ainda não implementa o fluxo de login.

O Compose usa a porta 80, mantém os dados no volume `postgres_data` e não publica os serviços nem o PostgreSQL diretamente no host. Para parar os contêineres sem apagar os dados, execute `docker compose down`. As senhas e chaves padrão servem apenas ao ambiente local isolado; para alterá-las, configure `compose.yaml` e `infra/postgres/init.sql` antes de criar o volume. `INTERNAL_API_KEY` pode ser definida em `.env` conforme `.env.example`.

No Windows, rode a jornada automatizada depois que os contêineres estiverem prontos:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\smoke.ps1
```

O script descreve uma jornada de verificação de cadastro, login, abertura, crédito inicial, consulta, transferência, idempotência, concorrência, extratos e comprovante. Ele ainda usa o contrato antigo com token e falhará até ser adaptado ao HTTP Basic e à abertura interna de conta.

## Organização

- `identity-service/`: cadastro e autenticação.
- `finance-service/`: contas, saldos e transferências.
- `web/`: aplicação Angular.
- `infra/`: proxy e ambiente Docker da primeira iteração.

Cada microsserviço tem seu próprio `pom.xml`, banco de dados lógico e migrations Flyway. Não há POM pai nem dependência Maven entre serviços. Os contratos, as migrations e a infraestrutura HTTP foram preservados como ponto de partida; as regras e jornadas descritas na especificação ainda precisam ser implementadas.

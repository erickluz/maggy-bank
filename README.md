# Maggy Bank

Banco digital fictício para estudo de engenharia de software. Nenhuma operação movimenta dinheiro real.

A primeira iteração está especificada em [docs/primeira-iteracao.md](docs/primeira-iteracao.md). Os contratos REST ficam em `contracts/`.

## Executar localmente

Requisitos: Docker com Compose. Na raiz do repositório:

```sh
docker compose up -d --build
```

Acesse `http://localhost`. Cadastre duas pessoas fictícias para experimentar uma transferência. Cada cadastro recebe R$ 1.000,00 simulados. A interface guarda o token somente em memória; ao recarregar a página, entre novamente.

O Compose usa a porta 80, mantém os dados no volume `postgres_data` e não publica os serviços nem o PostgreSQL diretamente no host. Para parar os contêineres sem apagar os dados, execute `docker compose down`. As senhas e chaves padrão servem apenas ao ambiente local isolado; para alterá-las, configure `compose.yaml` e `infra/postgres/init.sql` antes de criar o volume. `JWT_SECRET` e `INTERNAL_API_KEY` podem ser definidos em `.env` conforme `.env.example`.

No Windows, rode a jornada automatizada depois que os contêineres estiverem prontos:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\smoke.ps1
```

O script verifica cadastro, login, abertura, crédito inicial, consulta, transferência, repetição idempotente, disputa concorrente pelo saldo, extratos e comprovante.

## Organização

- `identity-service/`: cadastro e autenticação.
- `finance-service/`: contas, saldos e transferências.
- `web/`: aplicação Angular.
- `infra/`: proxy e ambiente Docker da primeira iteração.

Cada microsserviço tem seu próprio `pom.xml`, banco de dados lógico e migrations Flyway. Não há POM pai nem dependência Maven entre serviços. O Financeiro guarda centavos inteiros e faz débito, crédito, transferência e lançamentos em uma única transação PostgreSQL. A Identidade grava o cadastro como `PENDING` antes de pedir a abertura; tentativas periódicas e chamadas a login/`me` retomam a abertura. O Financeiro trata pedidos repetidos de abertura e transferência por suas chaves de idempotência.

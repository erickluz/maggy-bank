# Maggy Bank

Banco digital fictício para estudo de engenharia de software. Nenhuma operação movimenta dinheiro real.

Este repositório é um esqueleto de estudo: as operações de cadastro, login, conta e transferência, a persistência financeira e os fluxos correspondentes na interface estão pendentes de implementação. Os métodos indicam essas pendências com `TODO` e lançam exceções; não use esta aplicação como banco real.

A primeira iteração está especificada em [docs/primeira-iteracao.md](docs/primeira-iteracao.md). Os contratos REST ficam em `contracts/`.

## Executar localmente

Requisitos: Docker com Compose. Na raiz do repositório:

```sh
docker compose up -d --build
```

Acesse `http://localhost` para visualizar a estrutura da interface. As jornadas só funcionarão após a implementação das funcionalidades descritas na especificação.

O Compose usa a porta 80, mantém os dados no volume `postgres_data` e não publica os serviços nem o PostgreSQL diretamente no host. Para parar os contêineres sem apagar os dados, execute `docker compose down`. As senhas e chaves padrão servem apenas ao ambiente local isolado; para alterá-las, configure `compose.yaml` e `infra/postgres/init.sql` antes de criar o volume. `JWT_SECRET` e `INTERNAL_API_KEY` podem ser definidos em `.env` conforme `.env.example`.

No Windows, rode a jornada automatizada depois que os contêineres estiverem prontos:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\smoke.ps1
```

O script descreve uma jornada de verificação de cadastro, login, abertura, crédito inicial, consulta, transferência, idempotência, concorrência, extratos e comprovante. Ele falhará enquanto essas funcionalidades não forem implementadas. Os testes de `Money` foram mantidos como guia, mas estão desativados para permitir o empacotamento: remova `@Disabled` em `finance-service/src/test/java/bank/maggy/finance/domain/MoneyTest.java` quando começar a implementar o domínio.

## Organização

- `identity-service/`: cadastro e autenticação.
- `finance-service/`: contas, saldos e transferências.
- `web/`: aplicação Angular.
- `infra/`: proxy e ambiente Docker da primeira iteração.

Cada microsserviço tem seu próprio `pom.xml`, banco de dados lógico e migrations Flyway. Não há POM pai nem dependência Maven entre serviços. Os contratos, as migrations, a infraestrutura HTTP e os utilitários de token foram preservados como ponto de partida; as regras e jornadas descritas na especificação ainda precisam ser implementadas.

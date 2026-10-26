# Maggy Bank — especificação da primeira iteração

Estado: decisões de descoberta confirmadas pelo usuário. Este documento delimita a implementação inicial; decisões futuras ficam registradas sem antecipar sua implementação.

## Visão e escopo

Um cliente pessoa física fictícia cadastra-se na aplicação web, abre automaticamente uma conta Maggy Bank, recebe R$ 1.000,00 simulados uma única vez, consulta saldo e extrato, transfere para outra conta Maggy Bank e visualiza um comprovante web imprimível.

Cada cliente tem uma conta. O cadastro exige nome de exibição fictício, nome de usuário único e senha. Não exige CPF, endereço ou documentos. A conta nasce ativa. Não há bloqueio, encerramento, cheque especial, limite diário, agendamento, cartões, antifraude, notificações nem backoffice nesta iteração. A moeda é somente BRL simulado, com duas casas decimais.

Transferências são internas, imediatas e definitivas. O cliente informa o número da conta de destino, confere o nome do titular e confirma. A operação só é concluída quando o débito, o crédito e o registro da transferência estão persistidos juntos; caso contrário, nenhum saldo muda. O saldo não pode ficar negativo. Repetir a mesma tentativa não gera uma segunda movimentação. Saldo, extrato e comprovante do cliente refletem imediatamente uma transferência confirmada. Não há cancelamento pelo cliente; uma correção futura será outra movimentação rastreável.

O crédito inicial e cada transferência geram registros imutáveis de operação. O ledger de partidas dobradas fica fora desta iteração; sua inclusão futura não está decidida. Valores são representados no domínio por centavos inteiros, encapsulados em um tipo monetário. Os dados são sempre identificados como fictícios na interface.

## Arquitetura inicial

O monorepo contém dois microsserviços Spring Boot independentes e uma aplicação Angular. Identidade possui cadastro, credenciais, emissão de token e ID do cliente. Financeiro possui contas, números de conta, saldos, registros de crédito e transferência, extrato e comprovante. Não há módulo de domínio compartilhado, dependência Maven entre serviços nem POM pai. O Financeiro usa separação explícita de domínio e infraestrutura; a Identidade usa uma estrutura mais simples.

O Angular acessa uma entrada HTTP única em `localhost`. O proxy serve a aplicação e encaminha as APIs públicas. A API de abertura de conta fica restrita à rede interna Docker. A Identidade solicita a abertura por REST ao Financeiro, passando ID e nome de exibição; o Financeiro guarda esses dados associados à conta. A Identidade não guarda número de conta nem saldo. Os contratos REST são definidos antes do código em `contracts/`.

A autenticação inicial usa um token assinado de curta duração. O Angular o mantém apenas em memória e o envia como Bearer; recarregar a página exige novo login. O Financeiro valida o token e obtém dele o ID do cliente. Autenticação persistente e uma solução mais robusta ficam para depois.

Cada serviço possui seu próprio banco de dados lógico PostgreSQL, credenciais e migrations. No Docker local, ambos os bancos rodam em um único container PostgreSQL. O Financeiro executa a alteração de ambos os saldos e a gravação da operação em uma única transação do seu banco.

O ambiente inicial usa Docker Compose para testes de desenvolvimento. Não há implantação na AWS real. Não há Kafka, Outbox, MongoDB, BFF, ledger de partidas dobradas nem infraestrutura dedicada de observabilidade nesta iteração. O uso de Floci para emular EKS e outros serviços AWS ocorrerá em uma implementação futura. Kafka permanece o foco técnico dos incrementos posteriores, quando houver um consumidor com finalidade concreta.

## API e jornadas

Os contratos mínimos ficam em `contracts/identity.yaml` e `contracts/finance.yaml`. Eles cobrem cadastro, login, consulta da própria conta, extrato, resolução do destinatário, criação e consulta da transferência, além da abertura interna de conta.

Na abertura, a Identidade cria o cliente e solicita ao Financeiro a conta e o crédito inicial. Se a abertura ainda não estiver concluída, o cadastro permanece com estado pendente; o detalhe do mecanismo de recuperação ficou pendente na descoberta e deve ser resolvido no planejamento da implementação, sem mudar o contrato público silenciosamente.

Se a resposta da transferência se perder, a interface mostra que está verificando o resultado e consulta a mesma tentativa até conhecer o resultado definitivo. O comprovante web é obtido dos dados imutáveis da transferência, sem PDF armazenado.

## Critérios de conclusão

1. Dois clientes podem se cadastrar e entrar pela aplicação em `localhost`.
2. Cada cadastro cria exatamente uma conta ativa com crédito inicial único de R$ 1.000,00 simulado.
3. Um cliente vê número de conta, saldo e extrato com o crédito inicial.
4. Um cliente encontra um destinatário por número de conta, vê o nome e conclui uma transferência interna.
5. Débito, crédito e registro da transferência permanecem atômicos, inclusive sob solicitações concorrentes.
6. A repetição da mesma tentativa não duplica a transferência.
7. Ambos os clientes veem saldos e extratos atualizados; o remetente pode abrir e imprimir o comprovante web.
8. Cada serviço sobe com seu banco e migrations independentes no ambiente Docker Compose.

## Worktrees e sequência

O repositório começa com uma base comum versionada: esta especificação, os contratos OpenAPI, a estrutura de diretórios e as rotas compartilhadas. A partir desse commit, o trabalho segue em paralelo:

| Trilha | Propriedade |
| --- | --- |
| `work/identity-it1` | `identity-service/`: código, POM, migrations, imagem e testes do serviço de Identidade. |
| `work/finance-it1` | `finance-service/`: código, POM, migrations, imagem e testes do serviço Financeiro. |
| `work/web-it1` | `web/`: Angular, fluxos do cliente e respostas simuladas alinhadas ao OpenAPI. |
| `work/integration-it1` | `infra/`, Compose e proxy na raiz, sincronização dos contratos, integração e execução da jornada completa. |

Contratos compartilhados não são alterados unilateralmente nas três trilhas de componentes. Uma mudança é proposta, avaliada na trilha de integração e sincronizada com os consumidores afetados. A integração acontece em dois marcos: (1) cadastro, login, abertura, crédito inicial e saldo; (2) transferência, extrato e comprovante. A trilha de integração remove as respostas simuladas antes da entrega final.

## Pendências e riscos registrados

- O mecanismo exato de retomada da abertura de conta por REST após falha não foi fechado na entrevista. O estado pendente e a idempotência da abertura são requisitos já confirmados.
- A ausência de Outbox e Kafka na primeira iteração evita uma falsa promessa de entrega de eventos. A publicação confiável será uma decisão do incremento orientado a eventos.
- Sem ledger de partidas dobradas, a primeira versão tem rastreabilidade de operações, mas não uma contabilidade completa. Uma futura adoção exige plano de migração histórica.
- Sem observabilidade dedicada, diagnóstico distribuído ficará limitado nesta fase.

## Origem das decisões

As escolhas de escopo, stack, fronteiras, timing de Kafka/Mongo/Floci e divisão de worktrees acima foram confirmadas pelo usuário durante a descoberta. Nomes de diretórios, nomes de branches, forma concreta dos endpoints e detalhes internos são propostas de implementação registradas neste documento e nos contratos.

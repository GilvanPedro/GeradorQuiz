# QuizLab

Plataforma web para criar quizzes de questões objetivas, compartilhar por link e analisar os resultados.

Quem cria monta as questões no navegador (ou importa de um arquivo), envia o link e acompanha um relatório com
gráficos. Quem recebe o link responde em poucos cliques, com ou sem conta, e vê na hora o que acertou e o que errou.

**No ar:** <https://gerador-quiz-five.vercel.app>

![Relatório de um quiz, com distribuição das notas, desempenho por questão e tabela por pessoa](docs/imagens/relatorio.png)

## Sumário

- [Funcionalidades](#funcionalidades)
- [Telas](#telas)
- [Tecnologias e arquitetura](#tecnologias-e-arquitetura)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Rodar na sua máquina](#rodar-na-sua-máquina)
- [Configuração](#configuração)
- [Testes](#testes)
- [Publicar](#publicar)
- [Regras do sistema](#regras-do-sistema)
- [Arquivo de quiz](#arquivo-de-quiz)
- [Segurança e privacidade](#segurança-e-privacidade)
- [Modelo de dados](#modelo-de-dados)
- [API](#api)
- [Limitações conhecidas](#limitações-conhecidas)

## Funcionalidades

### Para quem cria

- **Três tipos de questão**, que podem ser misturados no mesmo quiz:

  | Tipo | Como funciona | Pontuação |
  |------|---------------|-----------|
  | Múltipla escolha | Uma única alternativa correta | Tudo ou nada |
  | Caixas de seleção | Uma ou mais alternativas corretas | Só pontua quem marca exatamente as corretas |
  | Verdadeiro ou falso | Uma ou mais afirmações para julgar | Proporcional: 3 de 4 afirmações valem 0,75 |

- **Explicação por questão** (opcional), mostrada a quem responde depois do envio.
- **Ordem aleatória** (opcional) das questões, das alternativas ou das duas, sorteada a cada abertura do quiz.
  Quem responde vê o resultado na mesma ordem em que respondeu; o autor vê tudo na ordem em que escreveu.
- **Compartilhamento por link.** O quiz pode ser público (aparece em *Explorar*) ou acessível só por quem tem o link.
- **Importar e exportar** quizzes em [arquivo JSON](#arquivo-de-quiz), para montar as questões fora do site.
- **Relatório** de cada quiz:
  - média, mediana, melhor e pior nota;
  - distribuição das notas por faixa;
  - questões ordenadas da mais errada para a mais acertada, com o que foi marcado em cada alternativa;
  - quantas vezes cada pessoa fez o quiz, com a primeira nota, a última, a melhor e a média;
  - a análise completa de cada tentativa;
  - exportação em **CSV** (Excel, LibreOffice, Google Planilhas) ou **impressão/PDF**.

### Para quem responde

- **Com ou sem conta.** Quem recebe o link pode responder informando só um nome.
- **Resultado na hora**, com a nota e a análise questão por questão: o que foi marcado, a resposta certa e a
  explicação do autor.
- **Histórico** (para quem tem conta) de todos os quizzes respondidos, com o percentual de acerto por tema.
- Quem respondeu sem conta recebe, no fim, um convite para criar uma. Se criar (ou entrar) em seguida, aquela
  tentativa vai para o histórico.

### Conta

- Cadastro com nome, e-mail e senha.
- Em *Minha conta*: mudar o nome de exibição, trocar a senha e excluir a conta com tudo o que é dela.
- Um e-mail por conta.

## Telas

| Explorar | Editor |
|----------|--------|
| ![Lista de quizzes públicos](docs/imagens/explorar.png) | ![Editor de quiz com as opções de publicação e ordem aleatória](docs/imagens/editor.png) |

| Responder sem conta | Resultado |
|---------------------|-----------|
| ![Quiz aberto pelo link, pedindo só o nome](docs/imagens/responder-sem-conta.png) | ![Resultado com a nota e a análise por questão](docs/imagens/resultado.png) |

A interface se adapta ao celular e acompanha o tema claro ou escuro do sistema.

## Tecnologias e arquitetura

| Parte | Tecnologia | Onde roda |
|-------|------------|-----------|
| API (`api/`) | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway | [Render](https://render.com) (Docker) |
| Interface (`web/`) | HTML, CSS e JavaScript puros, sem etapa de build | [Vercel](https://vercel.com) |
| Banco de dados | PostgreSQL | [Neon](https://neon.tech) |
| Testes | JUnit 5, MockMvc, H2 em memória | — |

```mermaid
flowchart LR
    navegador["Navegador"]
    subgraph vercel["Vercel"]
        web["Site estático<br/>web/"]
    end
    subgraph render["Render"]
        api["API REST<br/>Spring Boot"]
    end
    subgraph neon["Neon"]
        banco[("PostgreSQL")]
    end
    navegador -- "HTML, CSS, JS" --> web
    navegador -- "JSON + token (HTTPS)" --> api
    api -- "JDBC" --> banco
```

Decisões que explicam o desenho:

- **Site e API separados.** O site é só arquivos estáticos; toda regra (correção, permissões, limites) fica na API.
  O navegador conversa com a API por JSON, e a API autoriza o endereço do site por CORS.
- **Correção no servidor.** O gabarito nunca é enviado a quem está respondendo; as respostas vão para a API, que
  corrige e devolve a análise.
- **Sessões no banco.** O plano gratuito do Render suspende a API quando fica ociosa. Guardando as sessões no
  banco, ninguém é desconectado por causa disso.
- **Migrações versionadas.** As tabelas são criadas e alteradas pelo Flyway, na subida da API. O Hibernate só
  confere se o banco bate com o código (`ddl-auto=validate`).
- **Sem framework no front.** Um único `app.js` com roteamento por `#`. Todo texto vindo de usuários entra na
  página como nó de texto, nunca como HTML.

## Estrutura do projeto

```text
.
├── api/                                  API REST
│   ├── pom.xml
│   └── src/
│       ├── main/java/br/com/quizlab/
│       │   ├── ApiApp.java               ponto de entrada
│       │   ├── config/                   banco, CORS, formato dos erros, /api/saude
│       │   ├── conta/                    cadastro, login, sessões, Minha conta
│       │   ├── quiz/                     quizzes, questões e alternativas
│       │   └── tentativa/                respostas, correção, análise e relatório
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/             migrações do banco (V1, V2, …)
│       └── test/                         testes de integração
├── web/                                  site
│   ├── index.html
│   ├── app.js                            telas, rotas e chamadas à API
│   ├── styles.css
│   └── config.js                         endereço da API
├── docs/imagens/                         capturas usadas neste README
├── Dockerfile                            imagem da API (usada pelo Render)
├── render.yaml                           serviço da API no Render
├── vercel.json                           aponta a Vercel para a pasta web/
└── .env.example                          modelo das variáveis de ambiente
```

## Rodar na sua máquina

**Pré-requisitos:** Java 21, Maven 3.8+ e Python 3 (só para servir os arquivos do site; qualquer servidor de
arquivos estáticos serve).

1. Suba a API:

   ```bash
   cd api
   mvn spring-boot:run
   ```

   Ela responde em `http://localhost:8080`. Sem a variável `DATABASE_URL`, usa um banco H2 em memória, que zera a
   cada reinício — não é preciso instalar PostgreSQL para desenvolver.

2. Em outro terminal, sirva o site:

   ```bash
   python3 -m http.server 5173 -d web
   ```

3. Abra <http://localhost:5173> e crie uma conta.

Quando o site é aberto em `localhost`, ele chama a API local automaticamente (veja [`web/config.js`](web/config.js)).

Para usar um PostgreSQL de verdade (por exemplo, o da Neon) no desenvolvimento:

```bash
DATABASE_URL='postgresql://USUARIO:SENHA@HOST/BANCO?sslmode=require' mvn spring-boot:run
```

## Configuração

A API é configurada por variáveis de ambiente. Há um modelo em [`.env.example`](.env.example).

| Variável | Obrigatória | Padrão | Para que serve |
|----------|-------------|--------|----------------|
| `DATABASE_URL` | Em produção | H2 em memória | String de conexão do PostgreSQL, no formato que a Neon entrega (`postgresql://…`) ou em JDBC (`jdbc:postgresql://…`) |
| `CORS_ORIGENS` | Em produção | `*` | Endereços do site autorizados a chamar a API, separados por vírgula e sem barra no fim. Aceita curinga, como `https://meu-site-*.vercel.app` |
| `PORT` | Não | `8080` | Porta da API. O Render define sozinho |

No site, a única configuração é o endereço da API em produção, em [`web/config.js`](web/config.js).

## Testes

```bash
cd api
mvn test
```

São 34 testes, quase todos de integração: sobem a aplicação inteira contra um banco em memória e exercitam a API
de ponta a ponta. Cobrem cadastro e login, permissões, a correção de cada tipo de questão, edição e exclusão,
resposta sem conta, ordem aleatória, o relatório e as regras de conta.

## Publicar

O repositório já traz o que cada serviço precisa: [`render.yaml`](render.yaml), [`Dockerfile`](Dockerfile) e
[`vercel.json`](vercel.json). Os três têm plano gratuito.

1. **Neon.** Crie um projeto e copie a string de conexão (botão *Connect*). Não é preciso criar tabelas: a API
   faz isso na primeira subida.
2. **Render.** *New > Blueprint* e escolha este repositório. Ele lê o `render.yaml` e pede duas variáveis:
   - `DATABASE_URL`: a string da Neon;
   - `CORS_ORIGENS`: a URL do site na Vercel. Se ainda não tiver, use `*` e troque depois.
3. **Vercel.** Importe o mesmo repositório. Não há build; o `vercel.json` aponta para `web/`.
4. Em [`web/config.js`](web/config.js), troque `API_PRODUCAO` pela URL que o Render mostrou e faça o push.
5. No Render, ajuste `CORS_ORIGENS` para a URL final da Vercel.

A partir daí, cada push na `main` republica o site e a API. Quando há migração nova, ela roda na subida da API.

> [!NOTE]
> No plano gratuito do Render a API é suspensa depois de um tempo sem uso, e a primeira resposta seguinte leva
> até um minuto. O site avisa quando isso acontece.

## Regras do sistema

**Pontuação**

- Cada questão vale 1 ponto. A nota é mostrada em pontos e em percentual.
- Em verdadeiro ou falso, afirmação em branco conta como erro.

**Edição e exclusão de quizzes**

- Depois que outra pessoa responde, as questões não podem mais ser editadas, para o resultado dela não perder o
  sentido. A visibilidade (público ou só por link) continua podendo mudar.
- As respostas do próprio autor contam como teste: não travam a edição e são apagadas quando as questões mudam.
- Excluir um quiz apaga do banco o quiz, as questões, as alternativas e as respostas. Quem tem conta e já
  respondeu continua vendo o próprio resultado, porque cada tentativa guarda uma cópia da análise na hora da
  exclusão. Tentativas de quem respondeu sem conta são apagadas junto.

**Respostas sem conta**

- Só abrir e responder um quiz funcionam sem login; o resto do site exige conta.
- O resultado aparece uma vez, na tela seguinte ao envio. Saindo da página, ele some para a pessoa.
- Criando uma conta (ou entrando) em até 24 horas, no mesmo navegador, a última tentativa passa a ser da conta.
- Há um teto de 30 envios sem conta a cada 10 minutos por endereço de rede, para ninguém encher um relatório de
  respostas falsas. O valor fica em
  [`LimiteDeConvidados.java`](api/src/main/java/br/com/quizlab/tentativa/LimiteDeConvidados.java).

**Identificação no relatório**

- Quem tem conta é separado pelo e-mail, que é único. Duas contas com o mesmo nome não se misturam.
- Quem não tem conta é reconhecido só pelo nome digitado: "Ana Lima" e "ana  lima" contam como a mesma pessoa,
  e duas pessoas que digitarem o mesmo nome aparecem como uma só.

**Contas**

- Um e-mail só pode ter uma conta. Ele volta a ficar livre quando a conta é excluída ou passa 5 anos sem nenhum
  acesso; nesse segundo caso, a conta antiga é apagada quando alguém se cadastra com o mesmo e-mail.
- Trocar a senha exige a senha atual e desconecta a conta dos outros aparelhos.
- Excluir a conta exige a senha e apaga o cadastro, as sessões, os resultados e os quizzes da pessoa. Quem
  respondeu um quiz dela mantém o próprio resultado, com o autor exibido como "Conta excluída".

## Arquivo de quiz

Em *Meus quizzes* ou em *Criar quiz*, **Importar arquivo** abre um `.json` no editor para conferência; nada entra
no site antes de clicar em *Salvar quiz*. **Exportar** baixa um quiz seu nesse mesmo formato, e **Baixar modelo**
traz um exemplo com uma questão de cada tipo.

```json
{
  "formato": "quizlab-1",
  "titulo": "Revisão de História",
  "tema": "História",
  "descricao": "Capítulo 3",
  "publico": true,
  "embaralharQuestoes": false,
  "embaralharAlternativas": true,
  "questoes": [
    {
      "tipo": "UNICA",
      "enunciado": "Qual é a capital da França?",
      "explicacao": "Aparece no resultado, depois que a pessoa responde.",
      "alternativas": [
        { "texto": "Lyon", "correta": false },
        { "texto": "Paris", "correta": true }
      ]
    },
    {
      "tipo": "VERDADEIRO_FALSO",
      "enunciado": "Julgue as afirmações.",
      "alternativas": [
        { "texto": "A água ferve a 100 °C ao nível do mar", "correta": true },
        { "texto": "O Sol gira em torno da Terra", "correta": false }
      ]
    }
  ]
}
```

| Campo | Regra |
|-------|-------|
| `titulo` | Obrigatório, até 120 caracteres |
| `tema`, `descricao` | Opcionais (até 40 e 500 caracteres) |
| `publico` | `true` aparece em *Explorar*; `false` só abre por link. Sem o campo, vale `true` |
| `embaralharQuestoes`, `embaralharAlternativas` | Opcionais. Sem o campo, valem `false` |
| `questoes` | De 1 a 100 |
| `tipo` | `UNICA`, `MULTIPLA` ou `VERDADEIRO_FALSO` (também aceita `VF`). Sem o campo, o site deduz `UNICA` ou `MULTIPLA` pela quantidade de corretas |
| `enunciado` | Obrigatório, até 1000 caracteres |
| `explicacao` | Opcional, até 1000 caracteres |
| `alternativas` | De 2 a 10 (em verdadeiro ou falso, de 1 a 10 afirmações), até 500 caracteres cada |
| `correta` | Em verdadeiro ou falso, `true` quer dizer que a afirmação é verdadeira. Nos outros tipos, sem o campo vale `false` |

> [!WARNING]
> O arquivo exportado contém o gabarito. Não envie para quem vai responder.

O histórico de respostas não faz parte do arquivo; para isso, use a exportação do relatório.

## Segurança e privacidade

- **Senhas** são guardadas só como hash BCrypt, com sal individual. A senha em texto não é armazenada nem
  registrada em log.
- **Sessões** usam um token aleatório de 256 bits, válido por 30 dias. O banco guarda apenas o SHA-256 do token,
  então um vazamento do banco não entrega sessões prontas.
- **Login** responde com a mesma mensagem e em tempo parecido para "e-mail não existe" e "senha errada", para não
  revelar quais e-mails têm conta.
- **Gabarito** fica no servidor. Quem responde só recebe o enunciado e o texto das alternativas.
- **Permissões** são conferidas na API a cada chamada: só o autor edita, exclui ou vê o relatório de um quiz, e
  cada pessoa só vê as próprias tentativas.
- **Conteúdo de usuários** (títulos, enunciados, nomes) é inserido na página como texto, o que evita injeção de
  HTML. No CSV exportado, células que começariam com `=`, `+`, `-` ou `@` são neutralizadas para não virarem
  fórmulas na planilha.
- **O que o autor vê de quem responde:** o nome, o e-mail (de quem tem conta) e as respostas. A tela de responder
  avisa disso.

## Modelo de dados

```mermaid
erDiagram
    usuario ||--o{ sessao : "abre"
    usuario ||--o{ quiz : "cria"
    usuario |o--o{ tentativa : "faz"
    quiz ||--|{ questao : "tem"
    questao ||--|{ alternativa : "tem"
    quiz |o--o{ tentativa : "recebe"
    tentativa ||--o{ resposta : "marca"
    alternativa ||--o{ resposta : "é marcada em"

    usuario {
        bigint id PK
        varchar nome
        varchar email UK
        varchar senha_hash
        timestamp ultimo_acesso_em
    }
    sessao {
        varchar token_hash PK
        bigint usuario_id FK
        timestamp expira_em
    }
    quiz {
        bigint id PK
        varchar codigo UK
        bigint autor_id FK
        varchar titulo
        boolean publico
        boolean embaralhar_questoes
        boolean embaralhar_alternativas
    }
    questao {
        bigint id PK
        bigint quiz_id FK
        varchar tipo
        varchar enunciado
        varchar explicacao
    }
    alternativa {
        bigint id PK
        bigint questao_id FK
        varchar texto
        boolean correta
    }
    tentativa {
        bigint id PK
        bigint quiz_id FK "nulo se o quiz foi excluído"
        bigint usuario_id FK "nulo se respondeu sem conta"
        varchar convidado_nome
        bigint ordem_semente "ordem sorteada vista pela pessoa"
        numeric pontos
        integer total
        varchar analise "cópia em JSON, gravada ao excluir o quiz"
    }
    resposta {
        bigint tentativa_id FK
        bigint alternativa_id FK
        boolean valor
    }
```

As definições completas estão nas migrações, em
[`api/src/main/resources/db/migration`](api/src/main/resources/db/migration). Para mudar o banco, crie um arquivo
novo (`V6__descricao.sql`); os já aplicados não devem ser editados.

## API

Base: `/api`. As chamadas autenticadas levam `Authorization: Bearer <token>`, obtido no cadastro ou no login.
Erros voltam sempre como `{"erro": "mensagem"}`, com o status HTTP correspondente.

**Conta**

| Método e caminho | Login | O que faz |
|------------------|:-----:|-----------|
| `POST /api/contas` | Não | Cria a conta e devolve o token |
| `POST /api/login` | Não | Entra e devolve o token |
| `POST /api/logout` | Não | Encerra a sessão do token enviado |
| `GET /api/eu` | Sim | Dados de quem está logado |
| `PUT /api/eu` | Sim | Muda o nome de exibição |
| `PUT /api/eu/senha` | Sim | Troca a senha (pede a atual) |
| `POST /api/eu/excluir` | Sim | Exclui a conta (pede a senha) |

**Quizzes**

| Método e caminho | Login | O que faz |
|------------------|:-----:|-----------|
| `GET /api/quizzes` | Sim | Quizzes públicos |
| `GET /api/quizzes/meus` | Sim | Quizzes de quem está logado |
| `POST /api/quizzes` | Sim | Cria um quiz |
| `GET /api/quizzes/{codigo}` | Opcional | Quiz para responder, sem gabarito |
| `GET /api/quizzes/{codigo}/edicao` | Autor | Quiz completo, com gabarito |
| `PUT /api/quizzes/{codigo}` | Autor | Salva a edição |
| `PUT /api/quizzes/{codigo}/publico` | Autor | Publica ou tira do *Explorar* |
| `DELETE /api/quizzes/{codigo}` | Autor | Exclui o quiz |

**Tentativas e relatório**

| Método e caminho | Login | O que faz |
|------------------|:-----:|-----------|
| `POST /api/quizzes/{codigo}/tentativas` | Opcional | Envia as respostas e devolve a análise. Sem login, pede `nome` e devolve uma `chave` |
| `POST /api/tentativas/{id}/reivindicar` | Sim | Guarda na conta uma tentativa feita sem conta (pede a `chave`) |
| `GET /api/tentativas` | Sim | Histórico de quem está logado |
| `GET /api/tentativas/{id}` | Sim | Análise de uma tentativa (de quem fez ou do autor do quiz) |
| `GET /api/quizzes/{codigo}/tentativas` | Autor | Lista de tentativas do quiz |
| `GET /api/quizzes/{codigo}/relatorio` | Autor | Relatório completo do quiz |

`GET /api/saude` responde `{"status": "ok"}` e é usado pelo Render para saber se a API subiu.

<details>
<summary>Exemplo: responder um quiz sem conta</summary>

```bash
curl -X POST https://SUA-API.onrender.com/api/quizzes/CODIGO/tentativas \
  -H 'Content-Type: application/json' \
  -d '{
        "nome": "Zé da Silva",
        "respostas": [
          { "alternativaId": 12, "valor": true },
          { "alternativaId": 20, "valor": false }
        ]
      }'
```

Em questões de escolha, envie `valor: true` para cada alternativa marcada. Em verdadeiro ou falso, envie o
julgamento de cada afirmação. O que não for enviado conta como em branco.

</details>

## Limitações conhecidas

- Não há recuperação de senha nem confirmação de e-mail: quem esquece a senha não tem como recuperar a conta.
- Sem conta, a identificação é só pelo nome digitado, que qualquer pessoa pode repetir.
- O limite de envios sem conta fica na memória da API e zera quando ela reinicia.
- Em quizzes com ordem aleatória, o relatório e a análise vistos pelo autor seguem a ordem original das questões;
  só quem respondeu vê a ordem sorteada daquela tentativa.
- O autor não consegue apagar uma tentativa específica do relatório.

---

Desenvolvido por [Gilvan Pedro](https://github.com/GilvanPedro).

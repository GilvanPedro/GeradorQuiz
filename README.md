# QuizLab

Site para criar quizzes de questões objetivas, compartilhar por link e acompanhar os resultados.

| Parte | Tecnologia | Onde roda |
|-------|------------|-----------|
| API (`api/`) | Java 21 + Spring Boot | Render |
| Interface (`web/`) | HTML, CSS e JavaScript | Vercel |
| Banco de dados | PostgreSQL | Neon |

## O que já funciona

- **Conta:** nome, e-mail e senha. A senha é guardada só como hash BCrypt, nunca em texto. Tudo exige login.
- **Criar quiz:** três tipos de questão, que podem ser misturados no mesmo quiz:
  - *Múltipla escolha:* uma alternativa correta.
  - *Caixas de seleção:* uma ou mais corretas; só pontua quem marca exatamente elas.
  - *Verdadeiro ou falso:* uma ou mais afirmações para julgar; acertar parte delas dá ponto proporcional.
- **Compartilhar:** cada quiz tem um link (`…/#/q/codigo`). Marcado como público, também aparece em *Explorar*.
  Quem abre o link sem conta cria uma e volta direto para o quiz.
- **Meus resultados:** todo quiz respondido fica no histórico, com nota, acerto por tema e a análise de cada
  questão: o que foi marcado, a resposta certa e a explicação do autor.
- **Para o autor:** lista de quem respondeu cada quiz, com a nota e a análise de cada pessoa.

Regras que valem saber:

- O gabarito nunca vai para o navegador de quem está respondendo; a correção é feita na API.
- Depois que outra pessoa responde, as questões do quiz não podem mais ser editadas (o resultado dela perderia o
  sentido). Dá para mudar a visibilidade ou excluir.
- Excluir um quiz desativa o link, mas quem já respondeu continua vendo o próprio resultado.

## Rodar na sua máquina

Precisa de Java 21 e Maven.

```bash
cd api
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. Sem a variável `DATABASE_URL` ela usa um banco em memória, que zera a cada
reinício. Para usar a Neon:

```bash
DATABASE_URL='postgresql://USUARIO:SENHA@HOST/BANCO?sslmode=require' mvn spring-boot:run
```

Em outro terminal, sirva o site e abra `http://localhost:5173`:

```bash
python3 -m http.server 5173 -d web
```

Testes: `mvn test` dentro de `api/`.

## Publicar

1. **GitHub:** suba esta pasta para um repositório.
2. **Neon:** crie um projeto e copie a string de conexão (botão *Connect*). As tabelas são criadas sozinhas na
   primeira vez que a API sobe (Flyway, em `api/src/main/resources/db/migration`).
3. **Render:** *New > Blueprint*, escolha o repositório. Ele lê o `render.yaml` e pede duas variáveis:
   `DATABASE_URL` (a da Neon) e `CORS_ORIGENS` (a URL do site na Vercel; pode preencher depois do passo 4).
4. **Vercel:** importe o mesmo repositório. O `vercel.json` já aponta para a pasta `web/`; não há build.
5. Em [`web/config.js`](web/config.js), troque `API_PRODUCAO` pela URL que o Render mostrou e faça o push.
6. Volte ao Render e ajuste `CORS_ORIGENS` para a URL final da Vercel, sem barra no fim.

No plano gratuito do Render a API dorme depois de um tempo parada, e a primeira resposta leva até um minuto; o
site avisa quando isso acontece. As sessões ficam no banco, então ninguém é deslogado por causa disso.

## API

Tudo exige `Authorization: Bearer <token>`, menos criar conta, login, logout e `/api/saude`. Erros voltam como
`{"erro": "mensagem"}`.

| Método e caminho | O que faz |
|------------------|-----------|
| `POST /api/contas` | Cria a conta e já devolve o token |
| `POST /api/login`, `POST /api/logout`, `GET /api/eu` | Sessão |
| `GET /api/quizzes` | Quizzes públicos |
| `GET /api/quizzes/meus` | Quizzes de quem está logado |
| `POST /api/quizzes` | Cria um quiz |
| `GET /api/quizzes/{codigo}` | Quiz para responder (sem gabarito) |
| `GET /api/quizzes/{codigo}/edicao`, `PUT /api/quizzes/{codigo}` | Abrir e salvar a edição (só o autor) |
| `PUT /api/quizzes/{codigo}/publico` | Publicar ou tirar do Explorar |
| `DELETE /api/quizzes/{codigo}` | Excluir |
| `POST /api/quizzes/{codigo}/tentativas` | Envia as respostas e devolve a análise |
| `GET /api/quizzes/{codigo}/tentativas` | Quem respondeu (só o autor) |
| `GET /api/tentativas`, `GET /api/tentativas/{id}` | Histórico e análise de uma tentativa |

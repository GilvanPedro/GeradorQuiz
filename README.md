# QuizLab

Site para criar quizzes de questões objetivas, compartilhar por link e acompanhar os resultados.

| Parte | Tecnologia | Onde roda |
|-------|------------|-----------|
| API (`api/`) | Java 21 + Spring Boot | Render |
| Interface (`web/`) | HTML, CSS e JavaScript | Vercel |
| Banco de dados | PostgreSQL | Neon |

## O que já funciona

- **Conta:** nome, e-mail e senha. A senha é guardada só como hash BCrypt, nunca em texto.
  Em *Minha conta* dá para mudar o nome de exibição, trocar a senha (pede a atual e desconecta os outros
  aparelhos) e excluir a conta.
- **Um e-mail, uma conta.** O e-mail só volta a ficar livre se a conta for excluída ou passar 5 anos sem nenhum
  acesso; nesse caso, quem se cadastrar com ele começa uma conta nova e a antiga é apagada.
- **Criar quiz:** três tipos de questão, que podem ser misturados no mesmo quiz:
  - *Múltipla escolha:* uma alternativa correta.
  - *Caixas de seleção:* uma ou mais corretas; só pontua quem marca exatamente elas.
  - *Verdadeiro ou falso:* uma ou mais afirmações para julgar; acertar parte delas dá ponto proporcional.
- **Compartilhar:** cada quiz tem um link (`…/#/q/codigo`). Marcado como público, também aparece em *Explorar*.
- **Responder sem conta:** quem recebe o link responde informando só um nome. No fim vê o resultado completo e
  um convite para criar conta. Se criar (ou entrar) em seguida, aquela tentativa vai para o histórico dela; se
  não, o resultado some ao sair da página. Para o autor, a tentativa aparece no relatório marcada como "sem
  conta", e quem não tem conta é reconhecido pelo nome digitado. O resto do site continua exigindo login.
- **Meus resultados:** todo quiz respondido fica no histórico, com nota, acerto por tema e a análise de cada
  questão: o que foi marcado, a resposta certa e a explicação do autor.
- **Ordem aleatória (opcional):** o autor pode sortear a ordem das questões, a das alternativas, ou as duas. O
  sorteio é feito a cada vez que alguém abre o quiz; o resultado e o relatório seguem a ordem original.
- **Relatório para o autor:** média e mediana de acerto, distribuição das notas, questões da mais errada para a
  mais acertada (com o que foi marcado em cada alternativa), quantas vezes cada pessoa fez o quiz e como foi, e
  a análise de cada tentativa. Exporta em CSV (Excel, LibreOffice, Google Planilhas) ou imprime/salva em PDF.
- **Importar e exportar:** o quiz pode ser montado fora do site, num arquivo JSON, e importado; e qualquer quiz seu
  pode ser exportado para arquivo (veja [Arquivo de quiz](#arquivo-de-quiz)).

Regras que valem saber:

- Sem conta, a pessoa só é reconhecida pelo nome: "Ana Lima" e "ana  lima" contam como a mesma, e duas pessoas
  que digitarem o mesmo nome se misturam no relatório.
- Para ninguém encher um relatório de respostas falsas, há um teto de 30 envios sem conta a cada 10 minutos por
  endereço de rede. Uma turma inteira no mesmo wi-fi pode bater nesse teto; o número fica em
  [`LimiteDeConvidados.java`](api/src/main/java/br/com/quizlab/tentativa/LimiteDeConvidados.java).
- Quando um quiz é excluído, as tentativas de quem não tem conta são apagadas junto.
- O gabarito nunca vai para o navegador de quem está respondendo; a correção é feita na API.
- Depois que outra pessoa responde, as questões do quiz não podem mais ser editadas (o resultado dela perderia o
  sentido). Dá para mudar a visibilidade ou excluir.
- Excluir a conta apaga o cadastro, as sessões, os resultados e os quizzes da pessoa. Quem respondeu um quiz dela
  continua com o próprio resultado, mas o nome do autor vira "Conta excluída".
- Excluir um quiz apaga do banco o quiz, as questões, as alternativas e as respostas marcadas. Quem já respondeu
  continua vendo o próprio resultado: na hora da exclusão, cada tentativa guarda uma cópia pronta da análise.

## Arquivo de quiz

Em *Meus quizzes* ou em *Criar quiz*, **Importar arquivo** abre um `.json` no editor para você conferir e salvar;
nada entra no site antes de clicar em *Salvar quiz*. **Exportar** baixa um quiz seu nesse mesmo formato, e
**Baixar modelo** traz um exemplo com uma questão de cada tipo.

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
| `embaralharQuestoes`, `embaralharAlternativas` | Opcionais. `true` sorteia a ordem a cada vez que alguém abre o quiz. Sem o campo, vale `false` |
| `questoes` | De 1 a 100 |
| `tipo` | `UNICA` (uma correta), `MULTIPLA` (uma ou mais corretas) ou `VERDADEIRO_FALSO` (também aceita `VF`). Sem o campo, o site deduz `UNICA` ou `MULTIPLA` pela quantidade de corretas |
| `enunciado` | Obrigatório, até 1000 caracteres |
| `explicacao` | Opcional, até 1000 caracteres |
| `alternativas` | De 2 a 10 (em verdadeiro ou falso, de 1 a 10 afirmações), até 500 caracteres cada |
| `correta` | Em verdadeiro ou falso, `true` quer dizer que a afirmação é verdadeira. Nos outros tipos, sem o campo vale `false` |

O arquivo exportado contém o gabarito: não envie para quem vai responder. O histórico de respostas não é exportado.

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

Tudo exige `Authorization: Bearer <token>`, menos criar conta, login, logout, `/api/saude` e as duas rotas de
abrir e responder um quiz, que também funcionam sem conta. Erros voltam como
`{"erro": "mensagem"}`.

| Método e caminho | O que faz |
|------------------|-----------|
| `POST /api/contas` | Cria a conta e já devolve o token |
| `POST /api/login`, `POST /api/logout`, `GET /api/eu` | Sessão |
| `PUT /api/eu` | Muda o nome de exibição |
| `PUT /api/eu/senha` | Troca a senha (pede a atual) |
| `POST /api/eu/excluir` | Exclui a conta (pede a senha) |
| `GET /api/quizzes` | Quizzes públicos |
| `GET /api/quizzes/meus` | Quizzes de quem está logado |
| `POST /api/quizzes` | Cria um quiz |
| `GET /api/quizzes/{codigo}` | Quiz para responder (sem gabarito) |
| `GET /api/quizzes/{codigo}/edicao`, `PUT /api/quizzes/{codigo}` | Abrir e salvar a edição (só o autor) |
| `PUT /api/quizzes/{codigo}/publico` | Publicar ou tirar do Explorar |
| `DELETE /api/quizzes/{codigo}` | Excluir |
| `POST /api/quizzes/{codigo}/tentativas` | Envia as respostas e devolve a análise. Sem conta, pede `nome` e devolve uma `chave` |
| `POST /api/tentativas/{id}/reivindicar` | Guarda na conta uma tentativa feita sem conta (pede a `chave`, até 24 h depois) |
| `GET /api/quizzes/{codigo}/tentativas` | Quem respondeu (só o autor) |
| `GET /api/quizzes/{codigo}/relatorio` | Relatório completo do quiz (só o autor) |
| `GET /api/tentativas`, `GET /api/tentativas/{id}` | Histórico e análise de uma tentativa |

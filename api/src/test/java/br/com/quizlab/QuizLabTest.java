package br.com.quizlab;

import br.com.quizlab.conta.UsuarioRepository;
import br.com.quizlab.tentativa.LimpezaDeQuizzesExcluidos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuizLabTest {

    /** Uma questão de cada tipo. Gabarito: Q1 = Paris; Q2 = 2 e 3; Q3 = V, F, V, F. */
    private static final String QUIZ = """
            {"titulo": "Conhecimentos gerais", "tema": "Geral", "descricao": "Teste", "publico": %s, "questoes": [
              {"tipo": "UNICA", "enunciado": "Capital da França?", "explicacao": "Paris é a capital desde 987.",
               "alternativas": [{"texto": "Lyon", "correta": false}, {"texto": "Paris", "correta": true},
                                {"texto": "Nice", "correta": false}]},
              {"tipo": "MULTIPLA", "enunciado": "Quais são primos?",
               "alternativas": [{"texto": "2", "correta": true}, {"texto": "3", "correta": true},
                                {"texto": "4", "correta": false}]},
              {"tipo": "VERDADEIRO_FALSO", "enunciado": "Julgue as afirmações.",
               "alternativas": [{"texto": "A água ferve a 100 °C ao nível do mar", "correta": true},
                                {"texto": "O Sol gira em torno da Terra", "correta": false},
                                {"texto": "O Brasil fica na América do Sul", "correta": true},
                                {"texto": "Um triângulo tem quatro lados", "correta": false}]}
            ]}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private JdbcTemplate banco;
    @Autowired
    private LimpezaDeQuizzesExcluidos limpeza;

    // ---------- contas ----------

    @Test
    void criaContaEGuardaASenhaCriptografada() throws Exception {
        String email = emailNovo();
        criarConta("Ana", email, "segredo-123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nome").value("Ana"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist());

        String guardado = usuarios.findByEmail(email).orElseThrow().getSenhaHash();
        assertThat(guardado).startsWith("$2").doesNotContain("segredo-123");
    }

    @Test
    void recusaEmailRepetidoSenhaCurtaEEmailInvalido() throws Exception {
        String email = emailNovo();
        criarConta("Ana", email, "segredo-123").andExpect(status().isCreated());
        // O e-mail não diferencia maiúsculas de minúsculas.
        criarConta("Outra Ana", email.toUpperCase(), "segredo-456")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.erro").exists());
        criarConta("Bia", emailNovo(), "curta").andExpect(status().isBadRequest());
        criarConta("Bia", "nao-e-email", "segredo-123").andExpect(status().isBadRequest());
        criarConta("Bia", emailNovo(), "a".repeat(73)).andExpect(status().isBadRequest());
    }

    @Test
    void entraComASenhaCertaERecusaAErrada() throws Exception {
        String email = emailNovo();
        criarConta("Ana", email, "segredo-123");

        String token = JsonPath.read(corpo(entrar(email, "segredo-123").andExpect(status().isOk())), "$.token");
        mvc.perform(comToken(get("/api/eu"), token)).andExpect(jsonPath("$.email").value(email));

        entrar(email, "senha-errada").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.erro").exists());
        entrar(emailNovo(), "segredo-123").andExpect(status().isUnauthorized());
    }

    @Test
    void tudoExigeLoginMenosSaude() throws Exception {
        mvc.perform(get("/api/saude")).andExpect(status().isOk());
        mvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.erro").exists());
        mvc.perform(get("/api/tentativas").header(HttpHeaders.AUTHORIZATION, "Bearer inventado"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content(QUIZ.formatted(true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeixaDeValerDepoisDeSair() throws Exception {
        String token = novaPessoa("Ana");
        mvc.perform(comToken(get("/api/eu"), token)).andExpect(status().isOk());
        mvc.perform(comToken(post("/api/logout"), token)).andExpect(status().isNoContent());
        mvc.perform(comToken(get("/api/eu"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void corsLiberaOSiteInclusiveNasRecusas() throws Exception {
        mvc.perform(options("/api/quizzes/abc")
                        .header(HttpHeaders.ORIGIN, "https://quizlab.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(get("/api/quizzes").header(HttpHeaders.ORIGIN, "https://quizlab.vercel.app"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ---------- quizzes ----------

    @Test
    void quemRespondeNaoRecebeOGabarito() throws Exception {
        String autora = novaPessoa("Ana");
        String codigo = criarQuiz(autora, true);

        String visto = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), novaPessoa("Bia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meu").value(false))
                .andExpect(jsonPath("$.autor").value("Ana"))
                .andExpect(jsonPath("$.questoes", hasSize(3))));
        assertThat(visto).doesNotContain("correta").doesNotContain("explicacao").doesNotContain("987");

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/edicao"), autora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.questoes[0].alternativas[1].correta").value(true));
    }

    @Test
    void soOQuizPublicoApareceNaListaMasOLinkAbreOsDois() throws Exception {
        String autora = novaPessoa("Ana");
        String publico = criarQuiz(autora, true);
        String porLink = criarQuiz(autora, false);
        String visitante = novaPessoa("Bia");

        String lista = corpo(mvc.perform(comToken(get("/api/quizzes"), visitante)).andExpect(status().isOk()));
        List<String> codigos = JsonPath.read(lista, "$[*].codigo");
        assertThat(codigos).contains(publico).doesNotContain(porLink);
        mvc.perform(comToken(get("/api/quizzes/" + porLink), visitante)).andExpect(status().isOk());

        mvc.perform(comToken(get("/api/quizzes/meus"), autora))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].questoes").value(3))
                .andExpect(jsonPath("$[0].tentativas").value(0));
        mvc.perform(comToken(get("/api/quizzes/meus"), visitante)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(comToken(put("/api/quizzes/" + porLink + "/publico"), autora)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publico\": true}"))
                .andExpect(status().isNoContent());
        codigos = JsonPath.read(corpo(mvc.perform(comToken(get("/api/quizzes"), visitante))), "$[*].codigo");
        assertThat(codigos).contains(porLink);
    }

    @Test
    void recusaQuizMalMontado() throws Exception {
        String token = novaPessoa("Ana");
        enviarQuiz(token, """
                {"titulo": "", "publico": true, "questoes": [{"tipo": "VERDADEIRO_FALSO", "enunciado": "x",
                 "alternativas": [{"texto": "a", "correta": true}]}]}""").andExpect(status().isBadRequest());
        enviarQuiz(token, """
                {"titulo": "Sem questões", "publico": true, "questoes": []}""").andExpect(status().isBadRequest());
        enviarQuiz(token, """
                {"titulo": "Duas certas", "publico": true, "questoes": [{"tipo": "UNICA", "enunciado": "x",
                 "alternativas": [{"texto": "a", "correta": true}, {"texto": "b", "correta": true}]}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Questão 1: marque exatamente uma alternativa como correta."));
        enviarQuiz(token, """
                {"titulo": "Nenhuma certa", "publico": true, "questoes": [{"tipo": "MULTIPLA", "enunciado": "x",
                 "alternativas": [{"texto": "a", "correta": false}, {"texto": "b", "correta": false}]}]}""")
                .andExpect(status().isBadRequest());
        enviarQuiz(token, """
                {"titulo": "Tipo estranho", "publico": true, "questoes": [{"tipo": "DISSERTATIVA", "enunciado": "x",
                 "alternativas": [{"texto": "a", "correta": true}]}]}""").andExpect(status().isBadRequest());
    }

    // ---------- responder e ver o resultado ----------

    @Test
    void corrigeCadaTipoDeQuestaoEMostraAAnalise() throws Exception {
        String codigo = criarQuiz(novaPessoa("Ana"), true);
        String bia = novaPessoa("Bia");
        String quiz = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)));

        // Q1 certa; Q2 marcou só um dos dois primos (vale 0); Q3 acertou 3 de 4 e deixou a última em branco.
        String respostas = """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": false},
                 {"alternativaId": %d, "valor": true}]}""".formatted(
                id(quiz, 0, 1), id(quiz, 1, 0), id(quiz, 2, 0), id(quiz, 2, 1), id(quiz, 2, 2));

        String resultado = corpo(responder(bia, codigo, respostas)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pontos").value(1.75))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.minha").value(true))
                .andExpect(jsonPath("$.questoes[0].pontos").value(1))
                .andExpect(jsonPath("$.questoes[0].explicacao").value("Paris é a capital desde 987."))
                .andExpect(jsonPath("$.questoes[1].pontos").value(0))
                .andExpect(jsonPath("$.questoes[1].alternativas[0].valor").value(true))
                .andExpect(jsonPath("$.questoes[1].alternativas[1].correta").value(true))
                .andExpect(jsonPath("$.questoes[1].alternativas[1].valor").isEmpty())
                .andExpect(jsonPath("$.questoes[2].pontos").value(0.75))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].valor").value(false))
                .andExpect(jsonPath("$.questoes[2].alternativas[3].valor").isEmpty()));
        int tentativa = JsonPath.read(resultado, "$.id");

        // A análise continua lá depois, e aparece no histórico.
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos").value(1.75))
                .andExpect(jsonPath("$.quiz.titulo").value("Conhecimentos gerais"))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].valor").value(false));
        mvc.perform(comToken(get("/api/tentativas"), bia))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tentativa))
                .andExpect(jsonPath("$[0].tema").value("Geral"))
                .andExpect(jsonPath("$[0].autor").value("Ana"));
    }

    @Test
    void gabaritoCompletoValeNotaMaximaEEmBrancoValeZero() throws Exception {
        String codigo = criarQuiz(novaPessoa("Ana"), true);
        String bia = novaPessoa("Bia");
        String quiz = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)));

        responder(bia, codigo, """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": false}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": false}]}""".formatted(id(quiz, 0, 1), id(quiz, 1, 0), id(quiz, 1, 1),
                id(quiz, 2, 0), id(quiz, 2, 1), id(quiz, 2, 2), id(quiz, 2, 3)))
                .andExpect(jsonPath("$.pontos").value(3));

        responder(bia, codigo, "{\"respostas\": []}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pontos").value(0))
                .andExpect(jsonPath("$.questoes[0].respondida").value(false));
        // Marcar todas as caixas não é um jeito de acertar a questão de várias corretas.
        responder(bia, codigo, """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": true}]}""".formatted(id(quiz, 1, 0), id(quiz, 1, 1), id(quiz, 1, 2)))
                .andExpect(jsonPath("$.pontos").value(0));
        mvc.perform(comToken(get("/api/tentativas"), bia)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void recusaRespostasQueNaoFazemSentido() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String outro = corpo(mvc.perform(comToken(get("/api/quizzes/" + criarQuiz(ana, true)), ana)));
        String quiz = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), ana)));

        // Alternativa de outro quiz.
        responder(ana, codigo, """
                {"respostas": [{"alternativaId": %d, "valor": true}]}""".formatted(id(outro, 0, 1)))
                .andExpect(status().isBadRequest());
        // Duas marcadas numa questão de uma só correta.
        responder(ana, codigo, """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true}]}"""
                .formatted(id(quiz, 0, 0), id(quiz, 0, 1))).andExpect(status().isBadRequest());
        responder(ana, "naoexiste", "{\"respostas\": []}").andExpect(status().isNotFound());
        mvc.perform(comToken(get("/api/tentativas"), ana)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void resultadoEDaPessoaEDoAutorENaoDeTerceiros() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String bia = novaPessoa("Bia");
        int tentativa = JsonPath.read(corpo(responder(bia, codigo, "{\"respostas\": []}")), "$.id");

        mvc.perform(comToken(get("/api/tentativas/" + tentativa), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minha").value(false))
                .andExpect(jsonPath("$.respondente").value("Bia"));
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), novaPessoa("Caio"))).andExpect(status().isNotFound());

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/tentativas"), ana))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].respondente").value("Bia"));
        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/tentativas"), bia)).andExpect(status().isForbidden());
    }

    // ---------- editar e excluir ----------

    @Test
    void autorEditaEnquantoNinguemRespondeu() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        // O teste do próprio autor não trava a edição, e some quando as questões mudam.
        responder(ana, codigo, "{\"respostas\": []}").andExpect(status().isCreated());

        String novo = """
                {"titulo": "Só uma", "publico": false, "questoes": [{"tipo": "VERDADEIRO_FALSO",
                 "enunciado": "O céu é azul.", "alternativas": [{"texto": "Verdade", "correta": true}]}]}""";
        mvc.perform(comToken(put("/api/quizzes/" + codigo), ana).contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isNoContent());

        mvc.perform(comToken(get("/api/quizzes/" + codigo), ana))
                .andExpect(jsonPath("$.titulo").value("Só uma"))
                .andExpect(jsonPath("$.tema").doesNotExist())
                .andExpect(jsonPath("$.questoes", hasSize(1)));
        mvc.perform(comToken(get("/api/tentativas"), ana)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(comToken(put("/api/quizzes/" + codigo), novaPessoa("Bia"))
                        .contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isForbidden());
    }

    @Test
    void depoisQueOutraPessoaRespondeAsQuestoesNaoMudamMais() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        responder(novaPessoa("Bia"), codigo, "{\"respostas\": []}");

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/edicao"), ana))
                .andExpect(jsonPath("$.editavel").value(false));
        mvc.perform(comToken(put("/api/quizzes/" + codigo), ana)
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ.formatted(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void excluirApagaOQuizDoBancoMasOHistoricoDeQuemRespondeuContinua() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String bia = novaPessoa("Bia");
        String quiz = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)));
        // Q1 certa e Q3 com 1 de 4: a análise precisa sobreviver igualzinha.
        String respostas = """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": true}]}""".formatted(id(quiz, 0, 1), id(quiz, 2, 0), id(quiz, 2, 1));
        int tentativa = JsonPath.read(corpo(responder(bia, codigo, respostas)), "$.id");
        String antes = corpo(mvc.perform(comToken(get("/api/tentativas/" + tentativa), bia)));
        Long quizId = banco.queryForObject("select id from quiz where codigo = ?", Long.class, codigo);

        mvc.perform(comToken(delete("/api/quizzes/" + codigo), bia)).andExpect(status().isForbidden());
        mvc.perform(comToken(delete("/api/quizzes/" + codigo), ana)).andExpect(status().isNoContent());

        // Nada do quiz fica no banco.
        assertThat(contar("select count(*) from quiz where id = ?", quizId)).isZero();
        assertThat(contar("select count(*) from questao where quiz_id = ?", quizId)).isZero();
        assertThat(contar("select count(*) from alternativa where id = ?", (long) id(quiz, 0, 1))).isZero();
        assertThat(contar("select count(*) from resposta where tentativa_id = ?", (long) tentativa)).isZero();

        mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)).andExpect(status().isNotFound());
        mvc.perform(comToken(get("/api/quizzes/meus"), ana)).andExpect(jsonPath("$", hasSize(0)));
        responder(bia, codigo, "{\"respostas\": []}").andExpect(status().isNotFound());

        // A análise da Bia continua idêntica, só que o quiz não está mais disponível para refazer.
        String depois = corpo(mvc.perform(comToken(get("/api/tentativas/" + tentativa), bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.disponivel").value(false))
                .andExpect(jsonPath("$.quiz.codigo").isEmpty())
                .andExpect(jsonPath("$.quiz.titulo").value("Conhecimentos gerais"))
                .andExpect(jsonPath("$.quiz.autor").value("Ana"))
                .andExpect(jsonPath("$.pontos").value(1.25))
                .andExpect(jsonPath("$.questoes[0].explicacao").value("Paris é a capital desde 987."))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].valor").value(true))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].correta").value(false)));
        assertThat(JsonPath.<Object>read(depois, "$.questoes")).isEqualTo(JsonPath.<Object>read(antes, "$.questoes"));
        mvc.perform(comToken(get("/api/tentativas"), bia))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Conhecimentos gerais"))
                .andExpect(jsonPath("$[0].tema").value("Geral"))
                .andExpect(jsonPath("$[0].autor").value("Ana"));
        // O autor apagou o quiz: não acompanha mais as respostas dele.
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), ana)).andExpect(status().isNotFound());
    }

    @Test
    void excluirQuizQueNinguemRespondeuNaoDeixaNada() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, false);
        Long quizId = banco.queryForObject("select id from quiz where codigo = ?", Long.class, codigo);

        mvc.perform(comToken(delete("/api/quizzes/" + codigo), ana)).andExpect(status().isNoContent());

        assertThat(contar("select count(*) from quiz where id = ?", quizId)).isZero();
        assertThat(contar("select count(*) from questao where quiz_id = ?", quizId)).isZero();
        assertThat(contar("select count(*) from tentativa where quiz_id = ?", quizId)).isZero();
    }

    @Test
    void quizExcluidoPelaVersaoAntigaELimpoQuandoAApiSobe() throws Exception {
        String codigo = criarQuiz(novaPessoa("Ana"), true);
        String bia = novaPessoa("Bia");
        int tentativa = JsonPath.read(corpo(responder(bia, codigo, "{\"respostas\": []}")), "$.id");
        // Era assim que a versão antiga excluía: só marcava a data.
        banco.update("update quiz set excluido_em = current_timestamp where codigo = ?", codigo);

        limpeza.run(null);

        assertThat(contar("select count(*) from quiz where codigo = ?", codigo)).isZero();
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.disponivel").value(false))
                .andExpect(jsonPath("$.questoes", hasSize(3)));
    }

    // ---------- minha conta ----------

    @Test
    void mudarONomeValeTambemNosQuizzesENoHistoricoDosOutros() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String bia = novaPessoa("Bia");
        responder(bia, codigo, "{\"respostas\": []}");

        mvc.perform(comToken(put("/api/eu"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"  Ana Maria  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Maria"));

        mvc.perform(comToken(get("/api/eu"), ana)).andExpect(jsonPath("$.nome").value("Ana Maria"));
        mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)).andExpect(jsonPath("$.autor").value("Ana Maria"));
        mvc.perform(comToken(get("/api/tentativas"), bia)).andExpect(jsonPath("$[0].autor").value("Ana Maria"));
        mvc.perform(comToken(put("/api/eu"), ana).contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trocarASenhaExigeAAtualEDerrubaOsOutrosAparelhos() throws Exception {
        String email = emailNovo();
        String celular = JsonPath.read(corpo(criarConta("Ana", email, "segredo-123")), "$.token");
        String computador = JsonPath.read(corpo(entrar(email, "segredo-123")), "$.token");

        trocarSenha(computador, "senha-errada", "nova-senha-456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A senha atual não confere."));
        trocarSenha(computador, "segredo-123", "curta").andExpect(status().isBadRequest());
        trocarSenha(computador, "segredo-123", "nova-senha-456").andExpect(status().isNoContent());

        mvc.perform(comToken(get("/api/eu"), computador)).andExpect(status().isOk());
        mvc.perform(comToken(get("/api/eu"), celular)).andExpect(status().isUnauthorized());
        entrar(email, "segredo-123").andExpect(status().isUnauthorized());
        entrar(email, "nova-senha-456").andExpect(status().isOk());
        assertThat(usuarios.findByEmail(email).orElseThrow().getSenhaHash()).startsWith("$2").doesNotContain("nova-senha");
    }

    @Test
    void excluirAContaApagaTudoOQueEDela() throws Exception {
        String emailDaAna = emailNovo();
        String ana = JsonPath.read(corpo(criarConta("Ana", emailDaAna, "segredo-123")), "$.token");
        String bia = novaPessoa("Bia");
        String quizDaAna = criarQuiz(ana, true);
        String quizDaBia = criarQuiz(bia, true);
        int biaNoQuizDaAna = JsonPath.read(corpo(responder(bia, quizDaAna, "{\"respostas\": []}")), "$.id");
        responder(ana, quizDaBia, "{\"respostas\": []}").andExpect(status().isCreated());
        responder(ana, quizDaAna, "{\"respostas\": []}").andExpect(status().isCreated());
        Long idDaAna = usuarios.findByEmail(emailDaAna).orElseThrow().getId();

        excluirConta(ana, "senha-errada").andExpect(status().isBadRequest());
        mvc.perform(comToken(get("/api/eu"), ana)).andExpect(status().isOk());
        excluirConta(ana, "segredo-123").andExpect(status().isNoContent());

        // Nada dela fica no banco.
        assertThat(contar("select count(*) from usuario where id = ?", idDaAna)).isZero();
        assertThat(contar("select count(*) from sessao where usuario_id = ?", idDaAna)).isZero();
        assertThat(contar("select count(*) from quiz where autor_id = ?", idDaAna)).isZero();
        assertThat(contar("select count(*) from tentativa where usuario_id = ?", idDaAna)).isZero();
        assertThat(contar("select count(*) from tentativa where quiz_autor_id = ?", idDaAna)).isZero();
        mvc.perform(comToken(get("/api/eu"), ana)).andExpect(status().isUnauthorized());
        entrar(emailDaAna, "segredo-123").andExpect(status().isUnauthorized());

        // A Bia perde o quiz da Ana, mas não o próprio resultado nele, que fica sem o nome da Ana.
        mvc.perform(comToken(get("/api/quizzes/" + quizDaAna), bia)).andExpect(status().isNotFound());
        mvc.perform(comToken(get("/api/tentativas/" + biaNoQuizDaAna), bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.autor").value("Conta excluída"))
                .andExpect(jsonPath("$.questoes", hasSize(3)));
        // E o quiz da Bia continua de pé, sem a resposta da Ana.
        mvc.perform(comToken(get("/api/quizzes/" + quizDaBia + "/tentativas"), bia)).andExpect(jsonPath("$", hasSize(0)));

        // O e-mail fica livre na hora.
        criarConta("Ana de novo", emailDaAna, "segredo-789").andExpect(status().isCreated());
    }

    // ---------- um e-mail por conta ----------

    @Test
    void emailSoVoltaAFicarLivreDepoisDeCincoAnosSemAcesso() throws Exception {
        String email = emailNovo();
        String antiga = JsonPath.read(corpo(criarConta("Ana", email, "segredo-123")), "$.token");
        String codigo = criarQuiz(antiga, true);
        Long idAntigo = usuarios.findByEmail(email).orElseThrow().getId();

        banco.update("update usuario set ultimo_acesso_em = ? where id = ?", anosAtras(4), idAntigo);
        banco.update("update sessao set expira_em = ? where usuario_id = ?", anosAtras(3), idAntigo);
        criarConta("Outra pessoa", email, "segredo-456").andExpect(status().isConflict());

        banco.update("update usuario set ultimo_acesso_em = ? where id = ?", anosAtras(6), idAntigo);
        String nova = JsonPath.read(corpo(criarConta("Outra pessoa", email, "segredo-456")
                .andExpect(status().isCreated())), "$.token");

        // A conta antiga saiu inteira, e a nova começa vazia.
        assertThat(contar("select count(*) from usuario where email = ?", email)).isEqualTo(1);
        assertThat(contar("select count(*) from usuario where id = ?", idAntigo)).isZero();
        assertThat(contar("select count(*) from quiz where codigo = ?", codigo)).isZero();
        mvc.perform(comToken(get("/api/quizzes/meus"), nova)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(comToken(get("/api/eu"), nova)).andExpect(jsonPath("$.nome").value("Outra pessoa"));
        entrar(email, "segredo-123").andExpect(status().isUnauthorized());
        entrar(email, "segredo-456").andExpect(status().isOk());
    }

    @Test
    void usarOSiteRenovaOUltimoAcesso() throws Exception {
        String email = emailNovo();
        String token = JsonPath.read(corpo(criarConta("Ana", email, "segredo-123")), "$.token");
        Long id = usuarios.findByEmail(email).orElseThrow().getId();
        banco.update("update usuario set ultimo_acesso_em = ? where id = ?", anosAtras(4), id);

        mvc.perform(comToken(get("/api/quizzes"), token)).andExpect(status().isOk());

        assertThat(usuarios.findById(id).orElseThrow().getUltimoAcessoEm())
                .isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    // ---------- ordem aleatória ----------

    @Test
    void ordemAleatoriaEOpcionalEMudaSoOQueQuemRespondeVe() throws Exception {
        String ana = novaPessoa("Ana");
        String fixo = criarQuiz(ana, true);
        mvc.perform(comToken(get("/api/quizzes/" + fixo + "/edicao"), ana))
                .andExpect(jsonPath("$.embaralharQuestoes").value(false))
                .andExpect(jsonPath("$.embaralharAlternativas").value(false));
        String ordemOriginal = corpo(mvc.perform(comToken(get("/api/quizzes/" + fixo), ana)));
        for (int i = 0; i < 5; i++) {
            assertThat(corpo(mvc.perform(comToken(get("/api/quizzes/" + fixo), ana)))).isEqualTo(ordemOriginal);
        }

        String sorteado = JsonPath.read(corpo(enviarQuiz(ana, QUIZ.formatted(true)
                .replaceFirst("\\{", "{\"embaralharQuestoes\": true, \"embaralharAlternativas\": true, "))), "$.codigo");
        mvc.perform(comToken(get("/api/quizzes/" + sorteado + "/edicao"), ana))
                .andExpect(jsonPath("$.embaralharQuestoes").value(true))
                .andExpect(jsonPath("$.embaralharAlternativas").value(true))
                // Para o autor, a ordem em que ele escreveu não muda.
                .andExpect(jsonPath("$.questoes[0].enunciado").value("Capital da França?"))
                .andExpect(jsonPath("$.questoes[2].alternativas[3].texto").value("Um triângulo tem quatro lados"));

        Set<String> ordensDasQuestoes = new HashSet<>();
        Set<String> ordensDasAfirmacoes = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String visto = corpo(mvc.perform(comToken(get("/api/quizzes/" + sorteado), ana)));
            List<String> enunciados = JsonPath.read(visto, "$.questoes[*].enunciado");
            List<String> afirmacoes = JsonPath.read(visto, "$.questoes[?(@.tipo == 'VERDADEIRO_FALSO')].alternativas[*].texto");
            assertThat(enunciados).containsExactlyInAnyOrder("Capital da França?", "Quais são primos?", "Julgue as afirmações.");
            assertThat(afirmacoes).hasSize(4);
            ordensDasQuestoes.add(enunciados.toString());
            ordensDasAfirmacoes.add(afirmacoes.toString());
        }
        assertThat(ordensDasQuestoes).hasSizeGreaterThan(1);
        assertThat(ordensDasAfirmacoes).hasSizeGreaterThan(1);

        // A correção não depende da ordem em que as alternativas chegaram.
        String visto = corpo(mvc.perform(comToken(get("/api/quizzes/" + sorteado), ana)));
        List<Integer> paris = JsonPath.read(visto, "$.questoes[*].alternativas[?(@.texto == 'Paris')].id");
        responder(ana, sorteado, """
                {"respostas": [{"alternativaId": %d, "valor": true}]}""".formatted(paris.get(0)))
                .andExpect(jsonPath("$.pontos").value(1))
                .andExpect(jsonPath("$.questoes[0].enunciado").value("Capital da França?"));
    }

    // ---------- relatório do autor ----------

    @Test
    void relatorioResumeComoAsPessoasForamNoQuiz() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String bia = novaPessoa("Bia");
        String caio = novaPessoa("Caio");
        String quiz = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo), ana)));
        String gabarito = """
                {"respostas": [{"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": true}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": false}, {"alternativaId": %d, "valor": true},
                 {"alternativaId": %d, "valor": false}]}""".formatted(id(quiz, 0, 1), id(quiz, 1, 0), id(quiz, 1, 1),
                id(quiz, 2, 0), id(quiz, 2, 1), id(quiz, 2, 2), id(quiz, 2, 3));
        // Só a Q1, e errada (marcou Lyon).
        String soLyon = """
                {"respostas": [{"alternativaId": %d, "valor": true}]}""".formatted(id(quiz, 0, 0));

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.tentativas").value(0))
                .andExpect(jsonPath("$.resumo.media").value(0.0))
                .andExpect(jsonPath("$.questoes", hasSize(3)))
                .andExpect(jsonPath("$.pessoas", hasSize(0)));

        responder(bia, codigo, soLyon);      // Bia: 0%
        responder(bia, codigo, gabarito);    // Bia: 100%
        responder(caio, codigo, soLyon);     // Caio: 0%

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.titulo").value("Conhecimentos gerais"))
                .andExpect(jsonPath("$.quiz.questoes").value(3))
                .andExpect(jsonPath("$.resumo.tentativas").value(3))
                .andExpect(jsonPath("$.resumo.pessoas").value(2))
                .andExpect(jsonPath("$.resumo.media").value(33.3))
                .andExpect(jsonPath("$.resumo.mediana").value(0.0))
                .andExpect(jsonPath("$.resumo.melhor").value(100.0))
                .andExpect(jsonPath("$.resumo.pior").value(0.0))
                .andExpect(jsonPath("$.resumo.mediaDePontos").value(1.0))
                .andExpect(jsonPath("$.faixas", hasSize(5)))
                .andExpect(jsonPath("$.faixas[0].tentativas").value(2))
                .andExpect(jsonPath("$.faixas[4].rotulo").value("80–100%"))
                .andExpect(jsonPath("$.faixas[4].tentativas").value(1))
                // Q1: uma certa, duas erradas, e dá para ver que Lyon enganou duas vezes.
                .andExpect(jsonPath("$.questoes[0].acerto").value(33.3))
                .andExpect(jsonPath("$.questoes[0].certas").value(1))
                .andExpect(jsonPath("$.questoes[0].erradas").value(2))
                .andExpect(jsonPath("$.questoes[0].emBranco").value(0))
                .andExpect(jsonPath("$.questoes[0].alternativas[0].texto").value("Lyon"))
                .andExpect(jsonPath("$.questoes[0].alternativas[0].marcadas").value(2))
                .andExpect(jsonPath("$.questoes[0].alternativas[1].correta").value(true))
                .andExpect(jsonPath("$.questoes[0].alternativas[1].marcadas").value(1))
                // Q2 e Q3: duas em branco.
                .andExpect(jsonPath("$.questoes[1].emBranco").value(2))
                .andExpect(jsonPath("$.questoes[2].certas").value(1))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].falsas").value(1))
                .andExpect(jsonPath("$.questoes[2].alternativas[1].marcadas").value(0))
                // Quem fez mais vezes vem primeiro.
                .andExpect(jsonPath("$.pessoas", hasSize(2)))
                .andExpect(jsonPath("$.pessoas[0].nome").value("Bia"))
                .andExpect(jsonPath("$.pessoas[0].tentativas").value(2))
                .andExpect(jsonPath("$.pessoas[0].primeira").value(0.0))
                .andExpect(jsonPath("$.pessoas[0].ultima").value(100.0))
                .andExpect(jsonPath("$.pessoas[0].melhor").value(100.0))
                .andExpect(jsonPath("$.pessoas[0].media").value(50.0))
                .andExpect(jsonPath("$.pessoas[0].email", endsWith("@exemplo.com")))
                .andExpect(jsonPath("$.tentativas[0].email", endsWith("@exemplo.com")))
                .andExpect(jsonPath("$.pessoas[1].nome").value("Caio"))
                .andExpect(jsonPath("$.pessoas[1].tentativas").value(1))
                .andExpect(jsonPath("$.tentativas", hasSize(3)));

        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), bia)).andExpect(status().isForbidden());
    }

    // ---------- responder sem conta ----------

    @Test
    void quemTemOLinkRespondeSemContaInformandoUmNome() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, false);

        // Abre sem login, e continua sem receber o gabarito.
        String quiz = corpo(mvc.perform(get("/api/quizzes/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meu").value(false))
                .andExpect(jsonPath("$.questoes", hasSize(3))));
        assertThat(quiz).doesNotContain("correta").doesNotContain("explicacao");

        responderSemConta(codigo, "", "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o seu nome para responder."));
        String paris = """
                [{"alternativaId": %d, "valor": true}]""".formatted(id(quiz, 0, 1));
        String resultado = corpo(responderSemConta(codigo, "  Zé da Silva ", paris)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.respondente").value("Zé da Silva"))
                .andExpect(jsonPath("$.semConta").value(true))
                .andExpect(jsonPath("$.minha").value(true))
                .andExpect(jsonPath("$.pontos").value(1))
                .andExpect(jsonPath("$.chave").isNotEmpty())
                .andExpect(jsonPath("$.questoes[0].explicacao").value("Paris é a capital desde 987.")));
        int tentativa = JsonPath.read(resultado, "$.id");
        String chave = JsonPath.read(resultado, "$.chave");
        // A chave não fica guardada como veio, só o resumo dela.
        assertThat(banco.queryForObject("select chave_hash from tentativa where id = ?", String.class, tentativa))
                .hasSize(64).isNotEqualTo(chave);

        // Sem conta não há histórico: o resultado só existe na resposta acima.
        mvc.perform(get("/api/tentativas/" + tentativa)).andExpect(status().isUnauthorized());
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), novaPessoa("Curiosa"))).andExpect(status().isNotFound());
        // O resto do site continua fechado.
        mvc.perform(get("/api/quizzes/meus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quizzes/" + codigo + "/edicao")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quizzes/" + codigo + "/relatorio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quizzes/" + codigo).header(HttpHeaders.AUTHORIZATION, "Bearer vencido"))
                .andExpect(status().isUnauthorized());

        // O autor vê a tentativa e o que a pessoa marcou.
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respondente").value("Zé da Silva"))
                .andExpect(jsonPath("$.semConta").value(true))
                .andExpect(jsonPath("$.minha").value(false))
                .andExpect(jsonPath("$.chave").isEmpty())
                .andExpect(jsonPath("$.questoes[0].alternativas[1].valor").value(true));
        // E não pode mais mudar as questões.
        mvc.perform(comToken(put("/api/quizzes/" + codigo), ana)
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ.formatted(true)))
                .andExpect(status().isConflict());
    }

    @Test
    void relatorioJuntaPeloNomeQuemRespondeuSemConta() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        responderSemConta(codigo, "Zé da Silva", "[]");
        responderSemConta(codigo, "  zé  da SILVA ", "[]");
        responderSemConta(codigo, "Maria", "[]");
        // Uma pessoa com conta e o mesmo nome não se mistura com o convidado.
        responder(novaPessoa("Maria"), codigo, "{\"respostas\": []}");
        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(jsonPath("$.pessoas[?(@.semConta == true)].email", everyItem(nullValue())));

        String relatorio = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.tentativas").value(4))
                .andExpect(jsonPath("$.resumo.pessoas").value(3))
                .andExpect(jsonPath("$.pessoas[0].tentativas").value(2))
                .andExpect(jsonPath("$.pessoas[0].semConta").value(true))
                .andExpect(jsonPath("$.tentativas", hasSize(4))));
        assertThat(JsonPath.<String>read(relatorio, "$.pessoas[0].nome")).isEqualToIgnoringWhitespace("zé da SILVA");
        List<Boolean> marias = JsonPath.read(relatorio, "$.pessoas[?(@.nome == 'Maria')].semConta");
        assertThat(marias).containsExactlyInAnyOrder(true, false);
        List<Boolean> semConta = JsonPath.read(relatorio, "$.tentativas[*].semConta");
        assertThat(semConta).containsExactlyInAnyOrder(true, true, true, false);
        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/tentativas"), ana)).andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void quemCriaContaDepoisDeResponderFicaComOResultado() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String resultado = corpo(responderSemConta(codigo, "Zé", "[]"));
        int tentativa = JsonPath.read(resultado, "$.id");
        String chave = JsonPath.read(resultado, "$.chave");

        String ze = novaPessoa("José da Silva");
        reivindicar(ze, tentativa, "chave-inventada").andExpect(status().isNotFound());
        mvc.perform(post("/api/tentativas/" + tentativa + "/reivindicar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"chave\": \"%s\"}".formatted(chave))).andExpect(status().isUnauthorized());
        reivindicar(ze, tentativa, chave).andExpect(status().isNoContent());

        // Agora está no histórico dele, e o autor passa a ver o nome da conta.
        mvc.perform(comToken(get("/api/tentativas"), ze))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tentativa));
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), ze))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minha").value(true))
                .andExpect(jsonPath("$.semConta").value(false));
        mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(jsonPath("$.pessoas", hasSize(1)))
                .andExpect(jsonPath("$.pessoas[0].nome").value("José da Silva"))
                .andExpect(jsonPath("$.pessoas[0].semConta").value(false));

        // A chave vale uma vez só.
        reivindicar(novaPessoa("Esperto"), tentativa, chave).andExpect(status().isNotFound());
    }

    @Test
    void chaveDoConvidadoVencePoucoDepois() throws Exception {
        String codigo = criarQuiz(novaPessoa("Ana"), true);
        String resultado = corpo(responderSemConta(codigo, "Zé", "[]"));
        int tentativa = JsonPath.read(resultado, "$.id");
        banco.update("update tentativa set feita_em = ? where id = ?", OffsetDateTime.now(ZoneOffset.UTC).minusHours(25), tentativa);

        reivindicar(novaPessoa("José"), tentativa, JsonPath.read(resultado, "$.chave")).andExpect(status().isNotFound());
    }

    @Test
    void excluirOQuizApagaAsTentativasDeQuemNaoTemConta() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        int tentativa = JsonPath.read(corpo(responderSemConta(codigo, "Zé", "[]")), "$.id");

        mvc.perform(comToken(delete("/api/quizzes/" + codigo), ana)).andExpect(status().isNoContent());

        assertThat(contar("select count(*) from tentativa where id = ?", (long) tentativa)).isZero();
    }

    @Test
    void relatorioSeparaPeloEmailQuemTemContaComOMesmoNome() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String emailA = emailNovo();
        String emailB = emailNovo();
        String joaoA = JsonPath.read(corpo(criarConta("João Silva", emailA, "segredo-123")), "$.token");
        String joaoB = JsonPath.read(corpo(criarConta("João Silva", emailB, "segredo-123")), "$.token");
        responder(joaoA, codigo, "{\"respostas\": []}");
        responder(joaoA, codigo, "{\"respostas\": []}");
        int deB = JsonPath.read(corpo(responder(joaoB, codigo, "{\"respostas\": []}")), "$.id");

        String relatorio = corpo(mvc.perform(comToken(get("/api/quizzes/" + codigo + "/relatorio"), ana))
                .andExpect(jsonPath("$.resumo.pessoas").value(2))
                .andExpect(jsonPath("$.pessoas[0].nome").value("João Silva"))
                .andExpect(jsonPath("$.pessoas[0].email").value(emailA))
                .andExpect(jsonPath("$.pessoas[0].tentativas").value(2))
                .andExpect(jsonPath("$.pessoas[1].nome").value("João Silva"))
                .andExpect(jsonPath("$.pessoas[1].email").value(emailB))
                .andExpect(jsonPath("$.pessoas[1].tentativas").value(1)));
        List<String> emails = JsonPath.read(relatorio, "$.tentativas[*].email");
        assertThat(emails).containsExactlyInAnyOrder(emailA, emailA, emailB);
        mvc.perform(comToken(get("/api/tentativas/" + deB), ana)).andExpect(jsonPath("$.email").value(emailB));
    }

    // ---------- apoio ----------

    private long contar(String sql, Object... parametros) {
        return banco.queryForObject(sql, Long.class, parametros);
    }

    private ResultActions responderSemConta(String codigo, String nome, String respostas) throws Exception {
        String corpo = """
                {"nome": "%s", "respostas": %s}""".formatted(nome, respostas);
        return mvc.perform(post("/api/quizzes/" + codigo + "/tentativas")
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions reivindicar(String token, int tentativa, String chave) throws Exception {
        return mvc.perform(comToken(post("/api/tentativas/" + tentativa + "/reivindicar"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"chave\": \"%s\"}".formatted(chave)));
    }

    private ResultActions trocarSenha(String token, String atual, String nova) throws Exception {
        String corpo = """
                {"senhaAtual": "%s", "novaSenha": "%s"}""".formatted(atual, nova);
        return mvc.perform(comToken(put("/api/eu/senha"), token).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions excluirConta(String token, String senha) throws Exception {
        return mvc.perform(comToken(post("/api/eu/excluir"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\": \"%s\"}".formatted(senha)));
    }

    private static OffsetDateTime anosAtras(int anos) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusYears(anos);
    }

    private static String emailNovo() {
        return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
    }

    private ResultActions criarConta(String nome, String email, String senha) throws Exception {
        String corpo = """
                {"nome": "%s", "email": "%s", "senha": "%s"}""".formatted(nome, email, senha);
        return mvc.perform(post("/api/contas").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions entrar(String email, String senha) throws Exception {
        String corpo = """
                {"email": "%s", "senha": "%s"}""".formatted(email, senha);
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** Cria uma conta e devolve o token dela. */
    private String novaPessoa(String nome) throws Exception {
        return JsonPath.read(corpo(criarConta(nome, emailNovo(), "segredo-123")), "$.token");
    }

    private ResultActions enviarQuiz(String token, String quiz) throws Exception {
        return mvc.perform(comToken(post("/api/quizzes"), token).contentType(MediaType.APPLICATION_JSON).content(quiz));
    }

    private String criarQuiz(String token, boolean publico) throws Exception {
        return JsonPath.read(corpo(enviarQuiz(token, QUIZ.formatted(publico)).andExpect(status().isCreated())),
                "$.codigo");
    }

    private ResultActions responder(String token, String codigo, String respostas) throws Exception {
        return mvc.perform(comToken(post("/api/quizzes/" + codigo + "/tentativas"), token)
                .contentType(MediaType.APPLICATION_JSON).content(respostas));
    }

    /** Id da alternativa na posição dada, lido do quiz como quem responde recebe. */
    private static int id(String quiz, int questao, int alternativa) {
        return JsonPath.read(quiz, "$.questoes[%d].alternativas[%d].id".formatted(questao, alternativa));
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder pedido, String token) {
        return pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String corpo(ResultActions resposta) throws Exception {
        return resposta.andReturn().getResponse().getContentAsString();
    }
}

package br.com.quizlab;

import br.com.quizlab.conta.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
    void excluirEscondeOQuizMasPreservaOsResultados() throws Exception {
        String ana = novaPessoa("Ana");
        String codigo = criarQuiz(ana, true);
        String bia = novaPessoa("Bia");
        int tentativa = JsonPath.read(corpo(responder(bia, codigo, "{\"respostas\": []}")), "$.id");

        mvc.perform(comToken(delete("/api/quizzes/" + codigo), bia)).andExpect(status().isForbidden());
        mvc.perform(comToken(delete("/api/quizzes/" + codigo), ana)).andExpect(status().isNoContent());

        mvc.perform(comToken(get("/api/quizzes/" + codigo), bia)).andExpect(status().isNotFound());
        mvc.perform(comToken(get("/api/quizzes/meus"), ana)).andExpect(jsonPath("$", hasSize(0)));
        responder(bia, codigo, "{\"respostas\": []}").andExpect(status().isNotFound());
        mvc.perform(comToken(get("/api/tentativas/" + tentativa), bia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.disponivel").value(false))
                .andExpect(jsonPath("$.questoes", hasSize(3)));
    }

    // ---------- apoio ----------

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

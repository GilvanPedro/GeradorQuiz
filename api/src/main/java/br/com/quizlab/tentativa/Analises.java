package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.Questao;
import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.QuizRepository;
import br.com.quizlab.tentativa.TentativaDetalhe.AlternativaCorrigida;
import br.com.quizlab.tentativa.TentativaDetalhe.QuestaoCorrigida;
import br.com.quizlab.tentativa.TentativaDetalhe.QuizDaTentativa;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta a análise de uma tentativa. Enquanto o quiz existe, ela é calculada na hora a partir das questões e das
 * respostas. Quando o quiz é excluído, a análise de cada tentativa é congelada em JSON na própria tentativa, e aí
 * o quiz, as questões, as alternativas e as respostas podem sair do banco sem ninguém perder o histórico.
 */
@Component
public class Analises {

    private static final TypeReference<List<QuestaoCorrigida>> LISTA_DE_QUESTOES = new TypeReference<>() {
    };

    private final TentativaRepository tentativas;
    private final QuizRepository quizzes;
    private final ObjectMapper json;

    public Analises(TentativaRepository tentativas, QuizRepository quizzes, ObjectMapper json) {
        this.tentativas = tentativas;
        this.quizzes = quizzes;
        this.json = json;
    }

    TentativaDetalhe detalhe(Tentativa tentativa, Long quemPede) {
        boolean minha = !tentativa.isSemConta() && tentativa.getUsuario().getId().equals(quemPede);
        return detalhe(tentativa, minha, null);
    }

    /** O que o convidado recebe logo depois de responder: a análise e a chave para guardar numa conta. */
    TentativaDetalhe detalheDoConvidado(Tentativa tentativa, String chave) {
        return detalhe(tentativa, true, chave);
    }

    private TentativaDetalhe detalhe(Tentativa tentativa, boolean minha, String chave) {
        Quiz quiz = tentativa.getQuiz();
        List<QuestaoCorrigida> questoes = quiz != null ? corrigir(tentativa, quiz) : congeladas(tentativa);
        return new TentativaDetalhe(tentativa.getId(),
                new QuizDaTentativa(quiz == null ? null : quiz.getCodigo(), tentativa.getQuizTitulo(),
                        tentativa.getQuizTema(), tentativa.getQuizAutor(), quiz != null),
                tentativa.getRespondente(), tentativa.isSemConta(), minha,
                tentativa.getPontos(), tentativa.getTotal(), tentativa.getFeitaEm(), questoes, chave);
    }

    /** Apaga o quiz e tudo o que é dele, deixando em cada tentativa só a nota e a análise pronta. */
    @Transactional
    public void apagarQuiz(Long quizId) {
        Quiz quiz = quizzes.findById(quizId).orElse(null);
        if (quiz == null) {
            return;
        }
        for (Tentativa tentativa : tentativas.findByQuizId(quizId)) {
            if (tentativa.isSemConta()) {
                // Ninguém tem histórico para guardar isto: sai junto com o quiz.
                tentativas.delete(tentativa);
            } else {
                tentativa.congelar(paraJson(corrigir(tentativa, quiz)));
            }
        }
        // As respostas precisam sair antes das alternativas que elas apontam.
        tentativas.flush();
        quizzes.delete(quiz);
    }

    private static List<QuestaoCorrigida> corrigir(Tentativa tentativa, Quiz quiz) {
        Map<Long, Boolean> valores = new HashMap<>();
        tentativa.getRespostas().forEach(m -> valores.put(m.alternativaId(), m.valor()));
        return quiz.getQuestoes().stream().map(q -> corrigir(q, valores)).toList();
    }

    private static QuestaoCorrigida corrigir(Questao questao, Map<Long, Boolean> valores) {
        List<AlternativaCorrigida> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaCorrigida(a.getId(), a.getTexto(), a.isCorreta(), valores.get(a.getId())))
                .toList();
        return new QuestaoCorrigida(questao.getId(), questao.getTipo(), questao.getEnunciado(),
                questao.getExplicacao(), Corretor.pontos(questao, valores), Corretor.respondida(questao, valores),
                alternativas);
    }

    private String paraJson(List<QuestaoCorrigida> questoes) {
        try {
            String texto = json.writeValueAsString(questoes);
            // Só um quiz no limite de tamanho em tudo passaria disso; nesse caso ficam a nota e o título.
            return texto.length() <= Tentativa.TAMANHO_MAXIMO_DA_ANALISE ? texto : null;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<QuestaoCorrigida> congeladas(Tentativa tentativa) {
        if (tentativa.getAnalise() == null) {
            return List.of();
        }
        try {
            return json.readValue(tentativa.getAnalise(), LISTA_DE_QUESTOES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.Questao;
import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.TipoQuestao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A análise de uma tentativa: cada questão com o que foi marcado, o gabarito e a explicação. */
public record TentativaDetalhe(Long id, QuizDaTentativa quiz, String respondente, boolean minha, BigDecimal pontos,
                               int total, Instant feitaEm, List<QuestaoCorrigida> questoes) {

    /** {@code disponivel} é falso quando o quiz foi excluído e não dá mais para refazer. */
    public record QuizDaTentativa(String codigo, String titulo, String tema, String autor, boolean disponivel) {
    }

    public record QuestaoCorrigida(Long id, TipoQuestao tipo, String enunciado, String explicacao, BigDecimal pontos,
                                   boolean respondida, List<AlternativaCorrigida> alternativas) {
    }

    /** {@code valor} é nulo quando a pessoa não marcou (escolha) ou não julgou (verdadeiro ou falso). */
    public record AlternativaCorrigida(Long id, String texto, boolean correta, Boolean valor) {
    }

    static TentativaDetalhe de(Tentativa tentativa, Long quemPede) {
        Map<Long, Boolean> valores = new HashMap<>();
        tentativa.getRespostas().forEach(m -> valores.put(m.alternativaId(), m.valor()));

        Quiz quiz = tentativa.getQuiz();
        List<QuestaoCorrigida> questoes = quiz.getQuestoes().stream().map(q -> corrigir(q, valores)).toList();
        return new TentativaDetalhe(tentativa.getId(),
                new QuizDaTentativa(quiz.getCodigo(), quiz.getTitulo(), quiz.getTema(), quiz.getAutor().getNome(),
                        !quiz.isExcluido()),
                tentativa.getUsuario().getNome(), tentativa.getUsuario().getId().equals(quemPede),
                tentativa.getPontos(), tentativa.getTotal(), tentativa.getFeitaEm(), questoes);
    }

    private static QuestaoCorrigida corrigir(Questao questao, Map<Long, Boolean> valores) {
        List<AlternativaCorrigida> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaCorrigida(a.getId(), a.getTexto(), a.isCorreta(), valores.get(a.getId())))
                .toList();
        return new QuestaoCorrigida(questao.getId(), questao.getTipo(), questao.getEnunciado(),
                questao.getExplicacao(), Corretor.pontos(questao, valores), Corretor.respondida(questao, valores),
                alternativas);
    }
}

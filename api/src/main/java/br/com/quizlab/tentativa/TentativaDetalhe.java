package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.TipoQuestao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A análise de uma tentativa: cada questão com o que foi marcado, o gabarito e a explicação. {@code chave} só vem
 * preenchida na resposta a quem acabou de responder sem conta: é o que permite guardar a tentativa numa conta.
 */
public record TentativaDetalhe(Long id, QuizDaTentativa quiz, String respondente, String email, boolean semConta,
                               boolean minha,
                               BigDecimal pontos, int total, Instant feitaEm, List<QuestaoCorrigida> questoes,
                               String chave) {

    /** {@code disponivel} é falso (e {@code codigo} nulo) quando o quiz foi excluído e não dá mais para refazer. */
    public record QuizDaTentativa(String codigo, String titulo, String tema, String autor, boolean disponivel) {
    }

    public record QuestaoCorrigida(Long id, TipoQuestao tipo, String enunciado, String explicacao, BigDecimal pontos,
                                   boolean respondida, List<AlternativaCorrigida> alternativas) {
    }

    /** {@code valor} é nulo quando a pessoa não marcou (escolha) ou não julgou (verdadeiro ou falso). */
    public record AlternativaCorrigida(Long id, String texto, boolean correta, Boolean valor) {
    }
}

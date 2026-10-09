package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.Alternativa;
import br.com.quizlab.quiz.Questao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Calcula quanto vale a resposta dada a uma questão. Toda questão vale de 0 a 1 ponto. */
final class Corretor {

    private Corretor() {
    }

    /**
     * @param valores o que a pessoa enviou, por id de alternativa. Alternativa ausente = não marcada (escolha) ou
     *                deixada em branco (verdadeiro ou falso).
     */
    static BigDecimal pontos(Questao questao, Map<Long, Boolean> valores) {
        return switch (questao.getTipo()) {
            // Tudo ou nada: é preciso marcar exatamente as corretas, nem a mais nem a menos.
            case UNICA, MULTIPLA -> questao.getAlternativas().stream()
                    .allMatch(a -> a.isCorreta() == Boolean.TRUE.equals(valores.get(a.getId())))
                    ? BigDecimal.ONE : BigDecimal.ZERO;
            // Proporcional: acertar 3 de 4 afirmações vale 0,75. Em branco conta como erro.
            case VERDADEIRO_FALSO -> {
                long acertos = questao.getAlternativas().stream()
                        .filter(a -> valores.get(a.getId()) != null && valores.get(a.getId()) == a.isCorreta())
                        .count();
                yield BigDecimal.valueOf(acertos)
                        .divide(BigDecimal.valueOf(questao.getAlternativas().size()), 2, RoundingMode.HALF_UP);
            }
        };
    }

    static boolean respondida(Questao questao, Map<Long, Boolean> valores) {
        return questao.getAlternativas().stream().map(Alternativa::getId).anyMatch(valores::containsKey);
    }
}

package br.com.quizlab.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A ordem em que um quiz com sorteio aparece para uma pessoa. Tudo sai de um único número (a semente): com o mesmo
 * número a ordem é sempre a mesma. Por isso basta guardar a semente na tentativa para, depois, mostrar o resultado
 * na ordem em que a pessoa respondeu.
 */
public final class OrdemSorteada {

    private OrdemSorteada() {
    }

    /** Uma semente nova, ou nulo se o quiz não sorteia nada. Cabe num número de JavaScript sem perder dígitos. */
    public static Long novaSemente(Quiz quiz) {
        if (!quiz.isEmbaralharQuestoes() && !quiz.isEmbaralharAlternativas()) {
            return null;
        }
        return ThreadLocalRandom.current().nextLong(1L << 52);
    }

    /** As questões na ordem da semente; na ordem do autor se não houver semente ou o quiz não sortear questões. */
    public static List<Questao> questoes(Quiz quiz, Long semente) {
        List<Questao> questoes = new ArrayList<>(quiz.getQuestoes());
        if (semente != null && quiz.isEmbaralharQuestoes()) {
            Collections.shuffle(questoes, new Random(semente));
        }
        return questoes;
    }

    /** As alternativas de uma questão na ordem da semente. Cada questão tem o próprio embaralhamento. */
    public static List<Alternativa> alternativas(Quiz quiz, Questao questao, Long semente) {
        List<Alternativa> alternativas = new ArrayList<>(questao.getAlternativas());
        if (semente != null && quiz.isEmbaralharAlternativas()) {
            Collections.shuffle(alternativas, new Random(semente ^ questao.getId() * 0x9E3779B97F4A7C15L));
        }
        return alternativas;
    }
}

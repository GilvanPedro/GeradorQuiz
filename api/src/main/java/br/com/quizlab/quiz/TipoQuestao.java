package br.com.quizlab.quiz;

public enum TipoQuestao {
    /** Múltipla escolha com uma única alternativa correta. */
    UNICA,
    /** Caixas de seleção: uma ou mais alternativas corretas, e só vale se marcar exatamente elas. */
    MULTIPLA,
    /** Uma ou mais afirmações, cada uma julgada como verdadeira ou falsa. */
    VERDADEIRO_FALSO
}

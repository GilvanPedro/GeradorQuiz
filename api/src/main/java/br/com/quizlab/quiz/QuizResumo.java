package br.com.quizlab.quiz;

import java.time.Instant;

/** Uma linha das listas "Explorar" e "Meus quizzes". */
public record QuizResumo(String codigo, String titulo, String descricao, String tema, boolean publico,
                         Instant criadoEm, String autor, Integer questoes, Long tentativas) {
}

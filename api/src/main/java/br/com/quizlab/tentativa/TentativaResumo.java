package br.com.quizlab.tentativa;

import java.math.BigDecimal;
import java.time.Instant;

/** Uma linha de "Meus resultados" ou da lista de quem respondeu um quiz. {@code codigo} é nulo se o quiz foi excluído. */
public record TentativaResumo(Long id, String codigo, String titulo, String tema, String autor, String respondente,
                              boolean semConta,                               BigDecimal pontos, int total, Instant feitaEm) {
}

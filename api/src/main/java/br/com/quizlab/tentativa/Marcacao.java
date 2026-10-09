package br.com.quizlab.tentativa;

import jakarta.persistence.Embeddable;

/**
 * O que a pessoa fez com uma alternativa. Em questões de escolha só existem marcações com {@code valor = true}
 * (as alternativas marcadas); em verdadeiro ou falso, {@code valor} é o julgamento dado à afirmação.
 */
@Embeddable
public record Marcacao(Long alternativaId, boolean valor) {
}

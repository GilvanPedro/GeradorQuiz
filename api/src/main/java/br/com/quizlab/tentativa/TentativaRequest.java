package br.com.quizlab.tentativa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Escolha: mande {@code valor = true} para cada alternativa marcada. Verdadeiro ou falso: mande o julgamento de
 * cada afirmação. O que não vier conta como em branco.
 */
public record TentativaRequest(
        @NotNull(message = "Envie as respostas.")
        @Size(max = 1000, message = "Respostas demais.")
        List<@Valid @NotNull(message = "Resposta inválida.") RespostaRequest> respostas) {

    public record RespostaRequest(
            @NotNull(message = "Resposta inválida.") Long alternativaId,
            @NotNull(message = "Resposta inválida.") Boolean valor) {
    }
}

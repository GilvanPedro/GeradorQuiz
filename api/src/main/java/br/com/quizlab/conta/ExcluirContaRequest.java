package br.com.quizlab.conta;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(@NotBlank(message = "Informe a sua senha para confirmar.") String senha) {
}

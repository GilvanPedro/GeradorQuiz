package br.com.quizlab.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NomeRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 80, message = "O nome pode ter no máximo 80 caracteres.")
        String nome) {
}

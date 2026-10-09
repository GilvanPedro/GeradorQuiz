package br.com.quizlab.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SenhaRequest(
        @NotBlank(message = "Informe a senha atual.")
        String senhaAtual,

        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 8, message = "A nova senha precisa de pelo menos 8 caracteres.")
        String novaSenha) {
}

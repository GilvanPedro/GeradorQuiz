package br.com.quizlab.conta;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Informe o seu e-mail.") String email,
        @NotBlank(message = "Informe a sua senha.") String senha) {
}

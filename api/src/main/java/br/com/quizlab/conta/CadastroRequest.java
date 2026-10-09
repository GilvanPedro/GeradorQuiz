package br.com.quizlab.conta;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotBlank(message = "Informe o seu nome.")
        @Size(max = 80, message = "O nome pode ter no máximo 80 caracteres.")
        String nome,

        @NotBlank(message = "Informe o seu e-mail.")
        @Email(message = "Este e-mail não parece válido.")
        @Size(max = 160, message = "O e-mail pode ter no máximo 160 caracteres.")
        String email,

        @NotBlank(message = "Crie uma senha.")
        @Size(min = 8, message = "A senha precisa de pelo menos 8 caracteres.")
        String senha) {
}

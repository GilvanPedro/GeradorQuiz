package br.com.quizlab.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuizRequest(
        @NotBlank(message = "Dê um título ao quiz.")
        @Size(max = 120, message = "O título pode ter no máximo 120 caracteres.")
        String titulo,

        @Size(max = 500, message = "A descrição pode ter no máximo 500 caracteres.")
        String descricao,

        @Size(max = 40, message = "O tema pode ter no máximo 40 caracteres.")
        String tema,

        boolean publico,

        @NotNull(message = "O quiz precisa de pelo menos uma questão.")
        @Size(min = 1, max = 100, message = "O quiz precisa ter de 1 a 100 questões.")
        List<@Valid @NotNull(message = "Questão inválida.") QuestaoRequest> questoes) {

    public record QuestaoRequest(
            @NotNull(message = "Escolha o tipo de cada questão.")
            TipoQuestao tipo,

            @NotBlank(message = "Escreva o enunciado de todas as questões.")
            @Size(max = 1000, message = "O enunciado pode ter no máximo 1000 caracteres.")
            String enunciado,

            @Size(max = 1000, message = "A explicação pode ter no máximo 1000 caracteres.")
            String explicacao,

            @NotNull(message = "Toda questão precisa de alternativas.")
            @Size(min = 1, max = 10, message = "Cada questão pode ter no máximo 10 alternativas.")
            List<@Valid @NotNull(message = "Alternativa inválida.") AlternativaRequest> alternativas) {
    }

    public record AlternativaRequest(
            @NotBlank(message = "Preencha o texto de todas as alternativas.")
            @Size(max = 500, message = "Cada alternativa pode ter no máximo 500 caracteres.")
            String texto,

            boolean correta) {
    }
}

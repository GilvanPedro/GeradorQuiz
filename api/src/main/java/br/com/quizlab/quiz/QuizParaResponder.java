package br.com.quizlab.quiz;

import java.util.List;

/** O quiz como quem responde vê: sem gabarito e sem explicações. */
public record QuizParaResponder(String codigo, String titulo, String descricao, String tema, String autor,
                                boolean meu, List<QuestaoAberta> questoes) {

    public record QuestaoAberta(Long id, TipoQuestao tipo, String enunciado, List<AlternativaAberta> alternativas) {
    }

    public record AlternativaAberta(Long id, String texto) {
    }

    static QuizParaResponder de(Quiz quiz, Long quemPede) {
        List<QuestaoAberta> questoes = quiz.getQuestoes().stream()
                .map(q -> new QuestaoAberta(q.getId(), q.getTipo(), q.getEnunciado(),
                        q.getAlternativas().stream().map(a -> new AlternativaAberta(a.getId(), a.getTexto())).toList()))
                .toList();
        return new QuizParaResponder(quiz.getCodigo(), quiz.getTitulo(), quiz.getDescricao(), quiz.getTema(),
                quiz.getAutor().getNome(), quiz.getAutor().getId().equals(quemPede), questoes);
    }
}

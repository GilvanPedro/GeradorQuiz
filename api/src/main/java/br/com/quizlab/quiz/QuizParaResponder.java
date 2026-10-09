package br.com.quizlab.quiz;

import java.util.ArrayList;
import java.util.List;

/**
 * O quiz como quem responde vê: sem gabarito e sem explicações. Em quizzes com ordem aleatória, {@code sorteio} é o
 * número que gerou esta ordem; o site devolve esse número junto com as respostas, para o resultado ser mostrado
 * na mesma ordem.
 */
public record QuizParaResponder(String codigo, String titulo, String descricao, String tema, String autor,
                                boolean meu, Long sorteio, List<QuestaoAberta> questoes) {

    public record QuestaoAberta(Long id, TipoQuestao tipo, String enunciado, List<AlternativaAberta> alternativas) {
    }

    public record AlternativaAberta(Long id, String texto) {
    }

    static QuizParaResponder de(Quiz quiz, Long quemPede) {
        Long sorteio = OrdemSorteada.novaSemente(quiz);
        List<QuestaoAberta> questoes = new ArrayList<>();
        for (Questao q : OrdemSorteada.questoes(quiz, sorteio)) {
            List<AlternativaAberta> alternativas = OrdemSorteada.alternativas(quiz, q, sorteio).stream()
                    .map(a -> new AlternativaAberta(a.getId(), a.getTexto())).toList();
            questoes.add(new QuestaoAberta(q.getId(), q.getTipo(), q.getEnunciado(), alternativas));
        }
        return new QuizParaResponder(quiz.getCodigo(), quiz.getTitulo(), quiz.getDescricao(), quiz.getTema(),
                quiz.getAutor().getNome(), quiz.getAutor().getId().equals(quemPede), sorteio, questoes);
    }
}

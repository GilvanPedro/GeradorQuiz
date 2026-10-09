package br.com.quizlab.quiz;

import java.util.List;

/** O quiz completo, com gabarito. Só o autor recebe. */
public record QuizParaEditar(String codigo, String titulo, String descricao, String tema, boolean publico,
                             boolean editavel, List<QuestaoCompleta> questoes) {

    public record QuestaoCompleta(TipoQuestao tipo, String enunciado, String explicacao,
                                  List<AlternativaCompleta> alternativas) {
    }

    public record AlternativaCompleta(String texto, boolean correta) {
    }

    static QuizParaEditar de(Quiz quiz, boolean editavel) {
        List<QuestaoCompleta> questoes = quiz.getQuestoes().stream()
                .map(q -> new QuestaoCompleta(q.getTipo(), q.getEnunciado(), q.getExplicacao(),
                        q.getAlternativas().stream()
                                .map(a -> new AlternativaCompleta(a.getTexto(), a.isCorreta())).toList()))
                .toList();
        return new QuizParaEditar(quiz.getCodigo(), quiz.getTitulo(), quiz.getDescricao(), quiz.getTema(),
                quiz.isPublico(), editavel, questoes);
    }
}

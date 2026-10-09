package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.QuizRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na primeira versão, excluir só marcava o quiz como excluído e deixava tudo no banco. Ao subir, a API termina o
 * serviço para esses quizzes antigos. Depois da primeira vez não há mais nada para limpar.
 */
@Component
public class LimpezaDeQuizzesExcluidos implements ApplicationRunner {

    private final QuizRepository quizzes;
    private final Analises analises;

    public LimpezaDeQuizzesExcluidos(QuizRepository quizzes, Analises analises) {
        this.quizzes = quizzes;
        this.analises = analises;
    }

    @Override
    public void run(ApplicationArguments args) {
        quizzes.findByExcluidoEmIsNotNull().stream().map(Quiz::getId).forEach(analises::apagarQuiz);
    }
}

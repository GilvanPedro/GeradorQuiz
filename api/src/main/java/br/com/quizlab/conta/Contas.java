package br.com.quizlab.conta;

import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.QuizRepository;
import br.com.quizlab.tentativa.Analises;
import br.com.quizlab.tentativa.TentativaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Mudanças na conta que mexem em mais de uma tabela. */
@Component
public class Contas {

    /** O que aparece no histórico de quem respondeu um quiz cujo autor apagou a conta. */
    public static final String AUTOR_SEM_CONTA = "Conta excluída";

    private final UsuarioRepository usuarios;
    private final Sessoes sessoes;
    private final QuizRepository quizzes;
    private final TentativaRepository tentativas;
    private final Analises analises;

    public Contas(UsuarioRepository usuarios, Sessoes sessoes, QuizRepository quizzes,
                  TentativaRepository tentativas, Analises analises) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.quizzes = quizzes;
        this.tentativas = tentativas;
        this.analises = analises;
    }

    /** O nome novo vale também para o histórico de quem já respondeu quizzes desta pessoa. */
    @Transactional
    public void renomear(Long usuarioId, String nome) {
        usuarios.findById(usuarioId).orElseThrow().renomear(nome);
        tentativas.renomearAutor(usuarioId, nome);
    }

    /**
     * Apaga a conta e tudo o que é dela: os resultados que ela tem, os quizzes que criou (com questões e
     * respostas), as sessões e o cadastro. Quem respondeu um quiz dela continua com o próprio resultado, mas sem
     * o nome dela.
     */
    @Transactional
    public void apagar(Long usuarioId) {
        tentativas.deleteAll(tentativas.findByUsuarioId(usuarioId));
        tentativas.flush();
        quizzes.findByAutorId(usuarioId).stream().map(Quiz::getId).forEach(analises::apagarQuiz);
        tentativas.desligarAutor(usuarioId, AUTOR_SEM_CONTA);
        sessoes.encerrarTodas(usuarioId);
        usuarios.deleteById(usuarioId);
    }
}

package br.com.quizlab.quiz;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    String RESUMO = """
            select new br.com.quizlab.quiz.QuizResumo(q.codigo, q.titulo, q.descricao, q.tema, q.publico, q.criadoEm,
                a.nome, size(q.questoes), (select count(t) from Tentativa t where t.quiz = q))
            from Quiz q join q.autor a
            where q.excluidoEm is null""";

    Optional<Quiz> findByCodigoAndExcluidoEmIsNull(String codigo);

    boolean existsByCodigo(String codigo);

    @Query(RESUMO + " and q.publico = true order by q.criadoEm desc")
    List<QuizResumo> publicos(Pageable limite);

    @Query(RESUMO + " and a.id = :autorId order by q.criadoEm desc")
    List<QuizResumo> doAutor(Long autorId);

    /** O quiz do link, ou 404 se o código não existe ou o quiz foi excluído. */
    default Quiz exigir(String codigo) {
        return findByCodigoAndExcluidoEmIsNull(codigo).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Quiz não encontrado. O link pode estar errado ou o quiz foi excluído."));
    }
}

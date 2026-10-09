package br.com.quizlab.tentativa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TentativaRepository extends JpaRepository<Tentativa, Long> {

    String RESUMO = """
            select new br.com.quizlab.tentativa.TentativaResumo(t.id, q.codigo, q.titulo, q.tema, a.nome, u.nome,
                t.pontos, t.total, t.feitaEm)
            from Tentativa t join t.quiz q join q.autor a join t.usuario u""";

    @Query(RESUMO + " where u.id = :usuarioId order by t.feitaEm desc, t.id desc")
    List<TentativaResumo> doUsuario(Long usuarioId);

    @Query(RESUMO + " where q.id = :quizId order by t.feitaEm desc, t.id desc")
    List<TentativaResumo> doQuiz(Long quizId);

    List<Tentativa> findByQuizIdAndUsuarioId(Long quizId, Long usuarioId);

    boolean existsByQuizIdAndUsuarioIdNot(Long quizId, Long usuarioId);
}

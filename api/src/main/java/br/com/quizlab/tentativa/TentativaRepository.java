package br.com.quizlab.tentativa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TentativaRepository extends JpaRepository<Tentativa, Long> {

    String RESUMO = """
            select new br.com.quizlab.tentativa.TentativaResumo(t.id, q.codigo, t.quizTitulo, t.quizTema,
                t.quizAutor, u.nome, t.pontos, t.total, t.feitaEm)
            from Tentativa t left join t.quiz q join t.usuario u""";

    @Query(RESUMO + " where u.id = :usuarioId order by t.feitaEm desc, t.id desc")
    List<TentativaResumo> doUsuario(Long usuarioId);

    @Query(RESUMO + " where q.id = :quizId order by t.feitaEm desc, t.id desc")
    List<TentativaResumo> doQuiz(Long quizId);

    List<Tentativa> findByQuizId(Long quizId);

    List<Tentativa> findByUsuarioId(Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Tentativa t set t.quizAutor = :nome where t.quizAutorId = :autorId")
    void renomearAutor(Long autorId, String nome);

    /** O autor apagou a conta: o nome dele sai do histórico de quem respondeu. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Tentativa t set t.quizAutor = :nome, t.quizAutorId = null where t.quizAutorId = :autorId")
    void desligarAutor(Long autorId, String nome);

    List<Tentativa> findByQuizIdAndUsuarioId(Long quizId, Long usuarioId);

    boolean existsByQuizIdAndUsuarioIdNot(Long quizId, Long usuarioId);
}

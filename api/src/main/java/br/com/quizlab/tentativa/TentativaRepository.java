package br.com.quizlab.tentativa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TentativaRepository extends JpaRepository<Tentativa, Long> {

    String RESUMO = """
            select new br.com.quizlab.tentativa.TentativaResumo(t.id, q.codigo, t.quizTitulo, t.quizTema,
                t.quizAutor, coalesce(u.nome, t.convidadoNome), u.email, case when u.id is null then true else false end,
                t.pontos, t.total, t.feitaEm)
            from Tentativa t left join t.quiz q left join t.usuario u""";

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

    /** Alguém além do autor já respondeu? Conta também quem respondeu sem conta. */
    @Query("""
            select count(t) > 0 from Tentativa t
            where t.quiz.id = :quizId and (t.usuario is null or t.usuario.id <> :autorId)""")
    boolean respondidoPorOutros(Long quizId, Long autorId);

    /** A tentativa de convidado que ainda pode ser reivindicada com esta chave. */
    @Query("""
            select t from Tentativa t
            where t.id = :id and t.usuario is null and t.chaveHash = :chaveHash and t.feitaEm > :desde""")
    Optional<Tentativa> reivindicavel(Long id, String chaveHash, Instant desde);
}

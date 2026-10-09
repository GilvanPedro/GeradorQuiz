package br.com.quizlab.conta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, String> {

    @Query("""
            select new br.com.quizlab.conta.SessaoAtiva(u.id, u.nome, u.email, u.ultimoAcessoEm)
            from Sessao s join s.usuario u
            where s.tokenHash = :tokenHash and s.expiraEm > :agora""")
    Optional<SessaoAtiva> sessaoAtiva(String tokenHash, Instant agora);

    @Modifying
    @Query("delete from Sessao s where s.expiraEm < :agora")
    void apagarExpiradas(Instant agora);

    @Modifying
    @Query("delete from Sessao s where s.usuario.id = :usuarioId and s.tokenHash <> :manter")
    void apagarOutras(Long usuarioId, String manter);

    @Modifying
    @Query("delete from Sessao s where s.usuario.id = :usuarioId")
    void apagarDoUsuario(Long usuarioId);
}

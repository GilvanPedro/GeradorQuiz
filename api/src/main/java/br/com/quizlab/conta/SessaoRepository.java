package br.com.quizlab.conta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SessaoRepository extends JpaRepository<Sessao, String> {

    @Query("""
            select new br.com.quizlab.conta.UsuarioLogado(u.id, u.nome, u.email)
            from Sessao s join s.usuario u
            where s.tokenHash = :tokenHash and s.expiraEm > :agora""")
    Optional<UsuarioLogado> usuarioDaSessao(String tokenHash, Instant agora);

    @Modifying
    @Query("delete from Sessao s where s.expiraEm < :agora")
    void apagarExpiradas(Instant agora);
}

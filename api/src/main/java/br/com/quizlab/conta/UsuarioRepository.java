package br.com.quizlab.conta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    @Transactional
    @Modifying
    @Query("update Usuario u set u.ultimoAcessoEm = :agora where u.id = :id")
    void registrarAcesso(Long id, Instant agora);
}

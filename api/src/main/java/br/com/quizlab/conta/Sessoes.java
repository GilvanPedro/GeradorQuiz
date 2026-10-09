package br.com.quizlab.conta;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens de quem fez login. Ficam no banco (e não em memória) porque a API dorme no plano gratuito do Render, e
 * ninguém deveria ter que entrar de novo por causa disso.
 */
@Component
public class Sessoes {

    static final Duration VALIDADE = Duration.ofDays(30);
    private static final String PREFIXO = "Bearer ";

    private final SessaoRepository sessoes;
    private final SecureRandom sorteio = new SecureRandom();

    public Sessoes(SessaoRepository sessoes) {
        this.sessoes = sessoes;
    }

    @Transactional
    public String abrir(Usuario usuario) {
        Instant agora = Instant.now();
        sessoes.apagarExpiradas(agora);

        byte[] bytes = new byte[32];
        sorteio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.save(new Sessao(resumo(token), usuario, agora.plus(VALIDADE)));
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioLogado> usuarioDe(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessoes.usuarioDaSessao(resumo(token), Instant.now());
    }

    @Transactional
    public void encerrar(String token) {
        if (token != null && !token.isBlank()) {
            sessoes.deleteById(resumo(token));
        }
    }

    /** Lê o token do cabeçalho {@code Authorization: Bearer <token>}. */
    public static String tokenDe(String cabecalho) {
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            return null;
        }
        return cabecalho.substring(PREFIXO.length()).trim();
    }

    /** O token já é aleatório e longo, então um SHA-256 simples basta (diferente das senhas, que usam BCrypt). */
    static String resumo(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

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
    /** O último acesso é regravado no máximo uma vez por dia, para não escrever no banco a cada clique. */
    static final Duration INTERVALO_DO_ULTIMO_ACESSO = Duration.ofDays(1);
    private static final String PREFIXO = "Bearer ";

    private final SessaoRepository sessoes;
    private final UsuarioRepository usuarios;
    private final SecureRandom sorteio = new SecureRandom();

    public Sessoes(SessaoRepository sessoes, UsuarioRepository usuarios) {
        this.sessoes = sessoes;
        this.usuarios = usuarios;
    }

    @Transactional
    public String abrir(Usuario usuario) {
        Instant agora = Instant.now();
        sessoes.apagarExpiradas(agora);
        usuarios.registrarAcesso(usuario.getId(), agora);

        byte[] bytes = new byte[32];
        sorteio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.save(new Sessao(resumo(token), usuario, agora.plus(VALIDADE)));
        return token;
    }

    @Transactional
    public Optional<UsuarioLogado> usuarioDe(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = Instant.now();
        return sessoes.sessaoAtiva(resumo(token), agora).map(sessao -> {
            if (sessao.ultimoAcessoEm().isBefore(agora.minus(INTERVALO_DO_ULTIMO_ACESSO))) {
                usuarios.registrarAcesso(sessao.id(), agora);
            }
            return new UsuarioLogado(sessao.id(), sessao.nome(), sessao.email());
        });
    }

    @Transactional
    public void encerrar(String token) {
        if (token != null && !token.isBlank()) {
            sessoes.deleteById(resumo(token));
        }
    }

    /** Depois de trocar a senha: derruba os outros aparelhos e mantém só o que fez a troca. */
    @Transactional
    public void encerrarOutras(Long usuarioId, String tokenAtual) {
        sessoes.apagarOutras(usuarioId, tokenAtual == null ? "" : resumo(tokenAtual));
    }

    @Transactional
    public void encerrarTodas(Long usuarioId) {
        sessoes.apagarDoUsuario(usuarioId);
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

package br.com.quizlab.conta;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class ContaController {

    /** O BCrypt só considera os primeiros 72 bytes da senha; acima disso recusamos em vez de cortar em silêncio. */
    private static final int MAXIMO_DE_BYTES_DA_SENHA = 72;
    /** Depois desse tempo sem entrar, a conta é considerada abandonada e o e-mail pode ser usado numa conta nova. */
    static final int ANOS_ATE_LIBERAR_O_EMAIL = 5;

    private final UsuarioRepository usuarios;
    private final Sessoes sessoes;
    private final Contas contas;
    // Cada hash já leva o próprio sal; o custo 10 é o padrão e cabe na CPU do plano gratuito do Render.
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    /** Hash de uma senha qualquer, conferido quando o e-mail não existe, para o tempo de resposta não denunciar isso. */
    private final String hashDeMentira = bcrypt.encode("ninguem-tem-esta-senha");

    public ContaController(UsuarioRepository usuarios, Sessoes sessoes, Contas contas) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.contas = contas;
    }

    /** Um e-mail, uma conta. A exceção é a conta parada há mais de 5 anos, que dá lugar à nova. */
    @PostMapping("/contas")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse criar(@Valid @RequestBody CadastroRequest pedido) {
        conferirTamanho(pedido.senha());
        String email = normalizar(pedido.email());
        Usuario existente = usuarios.findByEmail(email).orElse(null);
        if (existente != null) {
            if (!abandonada(existente)) {
                throw jaExiste();
            }
            contas.apagar(existente.getId());
        }
        Usuario usuario;
        try {
            usuario = usuarios.saveAndFlush(new Usuario(pedido.nome().trim(), email, bcrypt.encode(pedido.senha())));
        } catch (DataIntegrityViolationException e) {
            // Duas pessoas (ou dois cliques) criando a mesma conta ao mesmo tempo: o banco barra a segunda.
            throw jaExiste();
        }
        return resposta(usuario);
    }

    @PostMapping("/login")
    public LoginResponse entrar(@Valid @RequestBody LoginRequest pedido) {
        Usuario usuario = usuarios.findByEmail(normalizar(pedido.email())).orElse(null);
        String hash = usuario == null ? hashDeMentira : usuario.getSenhaHash();
        boolean senhaConfere = !longaDemais(pedido.senha()) && bcrypt.matches(pedido.senha(), hash);
        if (usuario == null || !senhaConfere) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        return resposta(usuario);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String cabecalho) {
        sessoes.encerrar(Sessoes.tokenDe(cabecalho));
    }

    @GetMapping("/eu")
    public UsuarioLogado eu(@RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return eu;
    }

    @PutMapping("/eu")
    public UsuarioLogado mudarNome(@Valid @RequestBody NomeRequest pedido,
                                   @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        String nome = pedido.nome().trim();
        contas.renomear(eu.id(), nome);
        return new UsuarioLogado(eu.id(), nome, eu.email());
    }

    /** Exige a senha atual, e desconecta os outros aparelhos em que a conta estiver aberta. */
    @PutMapping("/eu/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void mudarSenha(@Valid @RequestBody SenhaRequest pedido,
                           @RequestHeader(HttpHeaders.AUTHORIZATION) String cabecalho,
                           @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        conferirTamanho(pedido.novaSenha());
        Usuario usuario = usuarios.findById(eu.id()).orElseThrow();
        conferirSenhaAtual(usuario, pedido.senhaAtual(), "A senha atual não confere.");
        usuario.trocarSenha(bcrypt.encode(pedido.novaSenha()));
        sessoes.encerrarOutras(eu.id(), Sessoes.tokenDe(cabecalho));
    }

    /** POST (e não DELETE) porque leva a senha no corpo, como confirmação. */
    @PostMapping("/eu/excluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@Valid @RequestBody ExcluirContaRequest pedido,
                             @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        conferirSenhaAtual(usuarios.findById(eu.id()).orElseThrow(), pedido.senha(), "A senha não confere.");
        contas.apagar(eu.id());
    }

    private LoginResponse resposta(Usuario usuario) {
        return new LoginResponse(sessoes.abrir(usuario),
                new UsuarioLogado(usuario.getId(), usuario.getNome(), usuario.getEmail()));
    }

    /** 400 e não 401: para o site, 401 quer dizer "sessão vencida", e aqui a pessoa só errou a senha. */
    private void conferirSenhaAtual(Usuario usuario, String senha, String mensagem) {
        if (longaDemais(senha) || !bcrypt.matches(senha, usuario.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
        }
    }

    private static boolean abandonada(Usuario usuario) {
        Instant limite = OffsetDateTime.now(ZoneOffset.UTC).minusYears(ANOS_ATE_LIBERAR_O_EMAIL).toInstant();
        return usuario.getUltimoAcessoEm().isBefore(limite);
    }

    private static ResponseStatusException jaExiste() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail. Tente entrar.");
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void conferirTamanho(String senha) {
        if (longaDemais(senha)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha pode ter no máximo 72 caracteres.");
        }
    }

    private static boolean longaDemais(String senha) {
        return senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_DE_BYTES_DA_SENHA;
    }
}

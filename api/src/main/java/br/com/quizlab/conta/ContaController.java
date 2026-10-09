package br.com.quizlab.conta;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class ContaController {

    /** O BCrypt só considera os primeiros 72 bytes da senha; acima disso recusamos em vez de cortar em silêncio. */
    private static final int MAXIMO_DE_BYTES_DA_SENHA = 72;

    private final UsuarioRepository usuarios;
    private final Sessoes sessoes;
    // Cada hash já leva o próprio sal; o custo 10 é o padrão e cabe na CPU do plano gratuito do Render.
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    /** Hash de uma senha qualquer, conferido quando o e-mail não existe, para o tempo de resposta não denunciar isso. */
    private final String hashDeMentira = bcrypt.encode("ninguem-tem-esta-senha");

    public ContaController(UsuarioRepository usuarios, Sessoes sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    @PostMapping("/contas")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse criar(@Valid @RequestBody CadastroRequest pedido) {
        if (longaDemais(pedido.senha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha pode ter no máximo 72 caracteres.");
        }
        String email = normalizar(pedido.email());
        if (usuarios.existsByEmail(email)) {
            throw jaExiste();
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

    private LoginResponse resposta(Usuario usuario) {
        return new LoginResponse(sessoes.abrir(usuario),
                new UsuarioLogado(usuario.getId(), usuario.getNome(), usuario.getEmail()));
    }

    private static ResponseStatusException jaExiste() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail. Tente entrar.");
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean longaDemais(String senha) {
        return senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_DE_BYTES_DA_SENHA;
    }
}

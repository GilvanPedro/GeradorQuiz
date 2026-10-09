package br.com.quizlab.conta;

/**
 * Quem fez a requisição. O {@link AutenticacaoInterceptor} guarda na requisição e os controllers recebem com
 * {@code @RequestAttribute(UsuarioLogado.ATRIBUTO)}.
 */
public record UsuarioLogado(Long id, String nome, String email) {

    public static final String ATRIBUTO = "usuarioLogado";
}

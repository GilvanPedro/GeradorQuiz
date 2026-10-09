package br.com.quizlab.conta;

public record LoginResponse(String token, UsuarioLogado usuario) {
}

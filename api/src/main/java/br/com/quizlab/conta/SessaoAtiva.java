package br.com.quizlab.conta;

import java.time.Instant;

/** O que a consulta de sessão devolve: quem é a pessoa e quando o último acesso dela foi anotado. */
public record SessaoAtiva(Long id, String nome, String email, Instant ultimoAcessoEm) {
}

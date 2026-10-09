package br.com.quizlab.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A Neon entrega a conexão como {@code postgresql://usuario:senha@host/banco?sslmode=require}, mas o driver JDBC
 * espera {@code jdbc:postgresql://host/banco?...} com usuário e senha à parte. Esta classe faz a tradução.
 */
public final class BancoDeDados {

    private BancoDeDados() {
    }

    /** Sem DATABASE_URL nada é configurado e o Spring sobe o H2 em memória (bom para rodar na sua máquina). */
    public static void configurar(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        Conexao conexao = traduzir(databaseUrl.trim());
        System.setProperty("spring.datasource.url", conexao.jdbcUrl());
        if (conexao.usuario() != null) {
            System.setProperty("spring.datasource.username", conexao.usuario());
        }
        if (conexao.senha() != null) {
            System.setProperty("spring.datasource.password", conexao.senha());
        }
    }

    static Conexao traduzir(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return new Conexao(databaseUrl, null, null);
        }
        URI uri = URI.create(databaseUrl);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL inválida: não foi possível ler o endereço do banco.");
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        String usuario = null;
        String senha = null;
        if (uri.getRawUserInfo() != null) {
            String[] partes = uri.getRawUserInfo().split(":", 2);
            usuario = decodificar(partes[0]);
            senha = partes.length > 1 ? decodificar(partes[1]) : null;
        }
        return new Conexao(jdbcUrl.toString(), usuario, senha);
    }

    private static String decodificar(String texto) {
        // URLDecoder trata "+" como espaço, o que não vale para a parte de usuário e senha da URL.
        return URLDecoder.decode(texto.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    record Conexao(String jdbcUrl, String usuario, String senha) {
    }
}

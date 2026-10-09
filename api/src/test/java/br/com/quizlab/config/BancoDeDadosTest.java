package br.com.quizlab.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BancoDeDadosTest {

    @Test
    void traduzAUrlDaNeonParaJdbc() {
        var conexao = BancoDeDados.traduzir(
                "postgresql://dono:s%40nha+1@ep-exemplo-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require");

        assertEquals("jdbc:postgresql://ep-exemplo-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require",
                conexao.jdbcUrl());
        assertEquals("dono", conexao.usuario());
        assertEquals("s@nha+1", conexao.senha());
    }
}

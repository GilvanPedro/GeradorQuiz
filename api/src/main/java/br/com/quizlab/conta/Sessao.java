package br.com.quizlab.conta;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class Sessao {

    @Id
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;
    private Instant expiraEm;

    protected Sessao() {
    }

    Sessao(String tokenHash, Usuario usuario, Instant expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.expiraEm = expiraEm;
    }
}

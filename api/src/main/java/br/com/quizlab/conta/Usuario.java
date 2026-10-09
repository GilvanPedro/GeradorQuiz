package br.com.quizlab.conta;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private Instant criadoEm;
    /** Atualizado no login e, no máximo uma vez por dia, enquanto a pessoa usa o site. */
    private Instant ultimoAcessoEm;

    protected Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = Instant.now();
        this.ultimoAcessoEm = criadoEm;
    }

    void renomear(String nome) {
        this.nome = nome;
    }

    void trocarSenha(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getUltimoAcessoEm() {
        return ultimoAcessoEm;
    }
}

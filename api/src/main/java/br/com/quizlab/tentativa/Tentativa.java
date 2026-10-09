package br.com.quizlab.tentativa;

import br.com.quizlab.conta.Usuario;
import br.com.quizlab.quiz.Quiz;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Uma vez que alguém respondeu um quiz. A mesma pessoa pode refazer; cada vez é uma tentativa nova. */
@Entity
public class Tentativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;
    /** Cada questão vale 1 ponto; verdadeiro ou falso com várias afirmações dá ponto proporcional. */
    @Column(precision = 7, scale = 2)
    private BigDecimal pontos;
    private int total;
    private Instant feitaEm;
    @ElementCollection
    @CollectionTable(name = "resposta", joinColumns = @JoinColumn(name = "tentativa_id"))
    private List<Marcacao> respostas = new ArrayList<>();

    protected Tentativa() {
    }

    Tentativa(Quiz quiz, Usuario usuario, BigDecimal pontos, int total, List<Marcacao> respostas) {
        this.quiz = quiz;
        this.usuario = usuario;
        this.pontos = pontos;
        this.total = total;
        this.feitaEm = Instant.now();
        this.respostas.addAll(respostas);
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public BigDecimal getPontos() {
        return pontos;
    }

    public int getTotal() {
        return total;
    }

    public Instant getFeitaEm() {
        return feitaEm;
    }

    public List<Marcacao> getRespostas() {
        return respostas;
    }
}

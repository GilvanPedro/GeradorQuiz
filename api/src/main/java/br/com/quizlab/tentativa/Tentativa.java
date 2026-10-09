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

    static final int TAMANHO_MAXIMO_DA_ANALISE = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Nulo depois que o quiz é excluído. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Quiz quiz;
    /** Nulo quando a pessoa respondeu sem conta; nesse caso vale {@link #convidadoNome}. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Usuario usuario;
    private String convidadoNome;
    /** SHA-256 da chave que permite ao convidado ficar com a tentativa ao criar uma conta. */
    private String chaveHash;
    // Cópia do que identifica o quiz, para o histórico continuar legível depois que ele for excluído.
    private String quizTitulo;
    private String quizTema;
    private String quizAutor;
    /** Para achar estas cópias quando o autor muda de nome ou apaga a conta. */
    private Long quizAutorId;
    /** Cada questão vale 1 ponto; verdadeiro ou falso com várias afirmações dá ponto proporcional. */
    @Column(precision = 7, scale = 2)
    private BigDecimal pontos;
    private int total;
    private Instant feitaEm;
    @ElementCollection
    @CollectionTable(name = "resposta", joinColumns = @JoinColumn(name = "tentativa_id"))
    private List<Marcacao> respostas = new ArrayList<>();
    /** A análise já corrigida, em JSON. Só existe depois que o quiz é excluído (ver {@link Analises}). */
    @Column(length = TAMANHO_MAXIMO_DA_ANALISE)
    private String analise;

    protected Tentativa() {
    }

    Tentativa(Quiz quiz, Usuario usuario, BigDecimal pontos, int total, List<Marcacao> respostas) {
        this.quiz = quiz;
        this.usuario = usuario;
        this.quizTitulo = quiz.getTitulo();
        this.quizTema = quiz.getTema();
        this.quizAutor = quiz.getAutor().getNome();
        this.quizAutorId = quiz.getAutor().getId();
        this.pontos = pontos;
        this.total = total;
        this.feitaEm = Instant.now();
        this.respostas.addAll(respostas);
    }

    /** Tentativa de quem respondeu sem conta. */
    static Tentativa deConvidado(Quiz quiz, String nome, String chaveHash, BigDecimal pontos, int total,
                                 List<Marcacao> respostas) {
        Tentativa tentativa = new Tentativa(quiz, null, pontos, total, respostas);
        tentativa.convidadoNome = nome;
        tentativa.chaveHash = chaveHash;
        return tentativa;
    }

    /** O convidado criou uma conta (ou entrou): a tentativa passa a ser dela e entra no histórico. */
    void entregarA(Usuario usuario) {
        this.usuario = usuario;
        this.convidadoNome = null;
        this.chaveHash = null;
    }

    public boolean isSemConta() {
        return usuario == null;
    }

    public String getRespondente() {
        return usuario == null ? convidadoNome : usuario.getNome();
    }

    /** Troca a ligação com o quiz e as respostas marcadas pela análise pronta, para o quiz poder ser apagado. */
    void congelar(String analise) {
        this.analise = analise;
        this.quiz = null;
        this.respostas.clear();
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

    public String getQuizTitulo() {
        return quizTitulo;
    }

    public String getQuizTema() {
        return quizTema;
    }

    public String getQuizAutor() {
        return quizAutor;
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

    public String getAnalise() {
        return analise;
    }
}

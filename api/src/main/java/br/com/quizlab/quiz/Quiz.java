package br.com.quizlab.quiz;

import br.com.quizlab.conta.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario autor;
    private String titulo;
    private String descricao;
    private String tema;
    private boolean publico;
    private Instant criadoEm;
    private Instant excluidoEm;
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<Questao> questoes = new ArrayList<>();

    protected Quiz() {
    }

    Quiz(String codigo, Usuario autor) {
        this.codigo = codigo;
        this.autor = autor;
        this.criadoEm = Instant.now();
    }

    /** Troca o conteúdo inteiro do quiz pelo que veio do formulário. */
    void preencher(QuizRequest pedido) {
        titulo = pedido.titulo().trim();
        descricao = textoOuNulo(pedido.descricao());
        tema = textoOuNulo(pedido.tema());
        publico = pedido.publico();

        questoes.clear();
        for (QuizRequest.QuestaoRequest q : pedido.questoes()) {
            Questao questao = new Questao(this, questoes.size(), q.tipo(), q.enunciado().trim(),
                    textoOuNulo(q.explicacao()));
            for (QuizRequest.AlternativaRequest a : q.alternativas()) {
                questao.adicionarAlternativa(a.texto().trim(), a.correta());
            }
            questoes.add(questao);
        }
    }

    void definirPublico(boolean publico) {
        this.publico = publico;
    }

    void excluir() {
        excluidoEm = Instant.now();
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Usuario getAutor() {
        return autor;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getTema() {
        return tema;
    }

    public boolean isPublico() {
        return publico;
    }

    public boolean isExcluido() {
        return excluidoEm != null;
    }

    public List<Questao> getQuestoes() {
        return questoes;
    }
}

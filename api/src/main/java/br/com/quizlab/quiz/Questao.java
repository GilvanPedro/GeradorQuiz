package br.com.quizlab.quiz;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;
    private int ordem;
    // Sem isto o Hibernate espera um tipo ENUM nativo no H2, e a coluna é um varchar comum.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private TipoQuestao tipo;
    private String enunciado;
    private String explicacao;
    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<Alternativa> alternativas = new ArrayList<>();

    protected Questao() {
    }

    Questao(Quiz quiz, int ordem, TipoQuestao tipo, String enunciado, String explicacao) {
        this.quiz = quiz;
        this.ordem = ordem;
        this.tipo = tipo;
        this.enunciado = enunciado;
        this.explicacao = explicacao;
    }

    void adicionarAlternativa(String texto, boolean correta) {
        alternativas.add(new Alternativa(this, alternativas.size(), texto, correta));
    }

    public Long getId() {
        return id;
    }

    public TipoQuestao getTipo() {
        return tipo;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public String getExplicacao() {
        return explicacao;
    }

    public List<Alternativa> getAlternativas() {
        return alternativas;
    }
}

package br.com.quizlab.quiz;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Em questões de verdadeiro ou falso é uma afirmação, e {@code correta} diz se ela é verdadeira. */
@Entity
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;
    private int ordem;
    private String texto;
    private boolean correta;

    protected Alternativa() {
    }

    Alternativa(Questao questao, int ordem, String texto, boolean correta) {
        this.questao = questao;
        this.ordem = ordem;
        this.texto = texto;
        this.correta = correta;
    }

    public Long getId() {
        return id;
    }

    public String getTexto() {
        return texto;
    }

    public boolean isCorreta() {
        return correta;
    }
}

package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.TipoQuestao;

import java.time.Instant;
import java.util.List;

/** Tudo o que o autor vê sobre como as pessoas foram no quiz dele. Percentuais vão de 0 a 100. */
public record Relatorio(QuizDoRelatorio quiz, Instant geradoEm, Resumo resumo, List<Faixa> faixas,
                        List<QuestaoDoRelatorio> questoes, List<PessoaDoRelatorio> pessoas,
                        List<TentativaResumo> tentativas) {

    public record QuizDoRelatorio(String codigo, String titulo, String tema, int questoes) {
    }

    /** {@code pessoas} conta cada pessoa uma vez, mesmo que tenha refeito o quiz. */
    public record Resumo(int tentativas, int pessoas, double media, double mediana, double melhor, double pior,
                         double mediaDePontos) {
    }

    /** Quantas tentativas terminaram com nota dentro da faixa (ex.: "40–59%"). */
    public record Faixa(String rotulo, int tentativas) {
    }

    /**
     * {@code acerto} é a média de pontos na questão, em percentual: numa questão de verdadeiro ou falso, acertar
     * metade das afirmações conta como 50.
     */
    public record QuestaoDoRelatorio(int numero, TipoQuestao tipo, String enunciado, double acerto, int certas,
                                     int parciais, int erradas, int emBranco,
                                     List<AlternativaDoRelatorio> alternativas) {
    }

    /**
     * Escolha: {@code marcadas} é quantas tentativas marcaram a alternativa. Verdadeiro ou falso: {@code marcadas}
     * é quantas julgaram "verdadeiro" e {@code falsas}, quantas julgaram "falso".
     */
    public record AlternativaDoRelatorio(String texto, boolean correta, int marcadas, int falsas) {
    }

    /**
     * Quem tem conta é separado pelo e-mail, que é único. Quem respondeu sem conta ({@code semConta}) não tem
     * e-mail e é agrupado pelo nome que digitou.
     */
    public record PessoaDoRelatorio(String nome, String email, boolean semConta, int tentativas, double melhor, double media, double primeira,
                                    double ultima, Instant ultimaEm) {
    }
}

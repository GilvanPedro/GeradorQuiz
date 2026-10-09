package br.com.quizlab.tentativa;

import br.com.quizlab.quiz.Alternativa;
import br.com.quizlab.quiz.Questao;
import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.tentativa.Relatorio.AlternativaDoRelatorio;
import br.com.quizlab.tentativa.Relatorio.Faixa;
import br.com.quizlab.tentativa.Relatorio.PessoaDoRelatorio;
import br.com.quizlab.tentativa.Relatorio.QuestaoDoRelatorio;
import br.com.quizlab.tentativa.Relatorio.QuizDoRelatorio;
import br.com.quizlab.tentativa.Relatorio.Resumo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calcula o relatório de um quiz a partir de todas as tentativas feitas nele. */
@Component
public class Relatorios {

    private static final String[] FAIXAS = {"0–19%", "20–39%", "40–59%", "60–79%", "80–100%"};

    private final TentativaRepository tentativas;

    public Relatorios(TentativaRepository tentativas) {
        this.tentativas = tentativas;
    }

    /** Precisa ser chamado dentro de uma transação: lê as questões e as respostas sob demanda. */
    Relatorio de(Quiz quiz) {
        List<Tentativa> feitas = tentativas.findByQuizId(quiz.getId()).stream()
                .sorted(Comparator.comparing(Tentativa::getFeitaEm).thenComparing(Tentativa::getId))
                .toList();
        List<Double> notas = feitas.stream().map(Relatorios::nota).sorted().toList();

        int[] porFaixa = new int[FAIXAS.length];
        notas.forEach(nota -> porFaixa[Math.min(FAIXAS.length - 1, (int) (nota / 20))]++);
        List<Faixa> faixas = new ArrayList<>();
        for (int i = 0; i < FAIXAS.length; i++) {
            faixas.add(new Faixa(FAIXAS[i], porFaixa[i]));
        }

        List<Map<Long, Boolean>> respostas = feitas.stream().map(Relatorios::valores).toList();
        List<QuestaoDoRelatorio> questoes = new ArrayList<>();
        for (Questao questao : quiz.getQuestoes()) {
            questoes.add(questao(questoes.size() + 1, questao, respostas));
        }

        Map<Long, List<Tentativa>> porPessoa = new LinkedHashMap<>();
        feitas.forEach(t -> porPessoa.computeIfAbsent(t.getUsuario().getId(), id -> new ArrayList<>()).add(t));
        List<PessoaDoRelatorio> pessoas = porPessoa.values().stream().map(Relatorios::pessoa)
                .sorted(Comparator.comparing(PessoaDoRelatorio::tentativas).reversed()
                        .thenComparing(PessoaDoRelatorio::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Resumo resumo = new Resumo(feitas.size(), pessoas.size(), media(notas), mediana(notas),
                notas.isEmpty() ? 0 : notas.get(notas.size() - 1), notas.isEmpty() ? 0 : notas.get(0),
                arredondar(feitas.stream().mapToDouble(t -> t.getPontos().doubleValue()).average().orElse(0), 2));
        return new Relatorio(
                new QuizDoRelatorio(quiz.getCodigo(), quiz.getTitulo(), quiz.getTema(), quiz.getQuestoes().size()),
                Instant.now(), resumo, faixas, questoes, pessoas, tentativas.doQuiz(quiz.getId()));
    }

    private static QuestaoDoRelatorio questao(int numero, Questao questao, List<Map<Long, Boolean>> respostas) {
        int certas = 0;
        int parciais = 0;
        int erradas = 0;
        int emBranco = 0;
        double pontos = 0;
        Map<Long, int[]> contagem = new HashMap<>();
        questao.getAlternativas().forEach(a -> contagem.put(a.getId(), new int[2]));

        for (Map<Long, Boolean> valores : respostas) {
            BigDecimal nota = Corretor.pontos(questao, valores);
            pontos += nota.doubleValue();
            if (!Corretor.respondida(questao, valores)) {
                emBranco++;
            } else if (nota.compareTo(BigDecimal.ONE) >= 0) {
                certas++;
            } else if (nota.signum() > 0) {
                parciais++;
            } else {
                erradas++;
            }
            for (Alternativa alternativa : questao.getAlternativas()) {
                Boolean valor = valores.get(alternativa.getId());
                if (valor != null) {
                    contagem.get(alternativa.getId())[valor ? 0 : 1]++;
                }
            }
        }

        List<AlternativaDoRelatorio> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaDoRelatorio(a.getTexto(), a.isCorreta(),
                        contagem.get(a.getId())[0], contagem.get(a.getId())[1]))
                .toList();
        double acerto = respostas.isEmpty() ? 0 : arredondar(pontos * 100 / respostas.size(), 1);
        return new QuestaoDoRelatorio(numero, questao.getTipo(), questao.getEnunciado(), acerto, certas, parciais,
                erradas, emBranco, alternativas);
    }

    /** As tentativas de uma mesma pessoa, já em ordem de data. */
    private static PessoaDoRelatorio pessoa(List<Tentativa> dela) {
        List<Double> notas = dela.stream().map(Relatorios::nota).toList();
        Tentativa ultima = dela.get(dela.size() - 1);
        return new PessoaDoRelatorio(ultima.getUsuario().getNome(), dela.size(),
                notas.stream().mapToDouble(Double::doubleValue).max().orElse(0), media(notas), notas.get(0),
                notas.get(notas.size() - 1), ultima.getFeitaEm());
    }

    private static Map<Long, Boolean> valores(Tentativa tentativa) {
        Map<Long, Boolean> valores = new HashMap<>();
        tentativa.getRespostas().forEach(m -> valores.put(m.alternativaId(), m.valor()));
        return valores;
    }

    /** Nota da tentativa em percentual, com uma casa decimal. */
    private static double nota(Tentativa tentativa) {
        return tentativa.getTotal() == 0 ? 0
                : arredondar(tentativa.getPontos().doubleValue() * 100 / tentativa.getTotal(), 1);
    }

    private static double media(List<Double> notas) {
        return arredondar(notas.stream().mapToDouble(Double::doubleValue).average().orElse(0), 1);
    }

    /** Recebe as notas já em ordem crescente. */
    private static double mediana(List<Double> notas) {
        if (notas.isEmpty()) {
            return 0;
        }
        int meio = notas.size() / 2;
        return notas.size() % 2 == 1 ? notas.get(meio) : arredondar((notas.get(meio - 1) + notas.get(meio)) / 2, 1);
    }

    private static double arredondar(double valor, int casas) {
        double fator = Math.pow(10, casas);
        return Math.round(valor * fator) / fator;
    }
}

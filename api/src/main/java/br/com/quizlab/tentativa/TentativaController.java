package br.com.quizlab.tentativa;

import br.com.quizlab.conta.Sessoes;
import br.com.quizlab.conta.UsuarioLogado;
import br.com.quizlab.conta.UsuarioRepository;
import br.com.quizlab.quiz.Alternativa;
import br.com.quizlab.quiz.Questao;
import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.QuizRepository;
import br.com.quizlab.quiz.TipoQuestao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TentativaController {

    private final TentativaRepository tentativas;
    private final QuizRepository quizzes;
    private final UsuarioRepository usuarios;
    private final Analises analises;
    private final Relatorios relatorios;
    private final LimiteDeConvidados limiteDeConvidados;
    private final SecureRandom sorteio = new SecureRandom();

    public TentativaController(TentativaRepository tentativas, QuizRepository quizzes, UsuarioRepository usuarios,
                               Analises analises, Relatorios relatorios,
                               LimiteDeConvidados limiteDeConvidados) {
        this.tentativas = tentativas;
        this.quizzes = quizzes;
        this.usuarios = usuarios;
        this.analises = analises;
        this.relatorios = relatorios;
        this.limiteDeConvidados = limiteDeConvidados;
    }

    /** Por quanto tempo quem respondeu sem conta ainda pode guardar a tentativa numa conta. */
    static final Duration PRAZO_PARA_REIVINDICAR = Duration.ofHours(24);

    /**
     * Recebe as respostas, corrige e já devolve a análise. Sem login, a pessoa informa um nome e a tentativa fica
     * registrada só para o autor do quiz, a não ser que ela crie uma conta em seguida (ver {@link #reivindicar}).
     */
    @PostMapping("/quizzes/{codigo}/tentativas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TentativaDetalhe responder(@PathVariable String codigo, @Valid @RequestBody TentativaRequest pedido,
                                      @RequestAttribute(value = UsuarioLogado.ATRIBUTO, required = false) UsuarioLogado eu,
                                      HttpServletRequest requisicao) {
        Quiz quiz = quizzes.exigir(codigo);
        String nome = pedido.nome() == null ? "" : pedido.nome().trim();
        if (eu == null) {
            if (nome.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o seu nome para responder.");
            }
            if (!limiteDeConvidados.permite(requisicao.getRemoteAddr())) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas respostas sem conta vindas da mesma rede. Espere alguns minutos ou entre numa conta.");
            }
        }
        Map<Long, Boolean> valores = new HashMap<>();
        pedido.respostas().forEach(r -> valores.put(r.alternativaId(), r.valor()));

        BigDecimal pontos = BigDecimal.ZERO;
        List<Marcacao> marcacoes = new ArrayList<>();
        for (Questao questao : quiz.getQuestoes()) {
            boolean escolha = questao.getTipo() != TipoQuestao.VERDADEIRO_FALSO;
            int marcadas = 0;
            for (Alternativa alternativa : questao.getAlternativas()) {
                Boolean valor = valores.get(alternativa.getId());
                // Em questões de escolha, "não marcada" e ausente são a mesma coisa: só as marcadas são guardadas.
                if (valor != null && (valor || !escolha)) {
                    marcacoes.add(new Marcacao(alternativa.getId(), valor));
                    marcadas++;
                }
            }
            if (questao.getTipo() == TipoQuestao.UNICA && marcadas > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Há uma questão de múltipla escolha com mais de uma alternativa marcada.");
            }
            pontos = pontos.add(Corretor.pontos(questao, valores));
        }
        long deOutroQuiz = valores.keySet().stream()
                .filter(id -> quiz.getQuestoes().stream()
                        .flatMap(q -> q.getAlternativas().stream()).noneMatch(a -> a.getId().equals(id)))
                .count();
        if (deOutroQuiz > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "As respostas não batem com este quiz. Recarregue a página e responda de novo.");
        }

        int total = quiz.getQuestoes().size();
        // A semente só muda a ordem em que a própria pessoa verá o resultado; não entra na correção.
        boolean sorteia = quiz.isEmbaralharQuestoes() || quiz.isEmbaralharAlternativas();
        Long semente = sorteia ? pedido.sorteio() : null;
        if (eu == null) {
            byte[] bytes = new byte[32];
            sorteio.nextBytes(bytes);
            String chave = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Tentativa tentativa = tentativas.save(
                    Tentativa.deConvidado(quiz, nome, Sessoes.resumo(chave), pontos, total, marcacoes)
                            .vistaNaOrdem(semente));
            return analises.detalheDoConvidado(tentativa, chave);
        }
        Tentativa tentativa = tentativas.save(
                new Tentativa(quiz, usuarios.getReferenceById(eu.id()), pontos, total, marcacoes)
                        .vistaNaOrdem(semente));
        return analises.detalhe(tentativa, eu.id());
    }

    /**
     * Quem respondeu sem conta e logo depois criou uma (ou entrou) fica com a tentativa: ela sai do nome digitado
     * e entra no histórico da conta. Precisa da chave que o navegador recebeu ao responder.
     */
    @PostMapping("/tentativas/{id}/reivindicar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reivindicar(@PathVariable Long id, @Valid @RequestBody ReivindicarRequest pedido,
                            @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Tentativa tentativa = tentativas
                .reivindicavel(id, Sessoes.resumo(pedido.chave()), Instant.now().minus(PRAZO_PARA_REIVINDICAR))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Não foi possível guardar este resultado na sua conta."));
        tentativa.entregarA(usuarios.getReferenceById(eu.id()));
    }

    public record ReivindicarRequest(@NotBlank(message = "Chave inválida.") String chave) {
    }

    @GetMapping("/tentativas")
    @Transactional(readOnly = true)
    public List<TentativaResumo> minhas(@RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return tentativas.doUsuario(eu.id());
    }

    /**
     * Quem fez a tentativa vê a própria análise; o autor do quiz vê a de todo mundo que respondeu, enquanto o quiz
     * existir.
     */
    @GetMapping("/tentativas/{id}")
    @Transactional(readOnly = true)
    public TentativaDetalhe detalhe(@PathVariable Long id, @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Tentativa tentativa = tentativas.findById(id)
                .filter(t -> !t.isSemConta() && t.getUsuario().getId().equals(eu.id())
                        || t.getQuiz() != null && t.getQuiz().getAutor().getId().equals(eu.id()))
                // Mesma resposta para "não existe" e "não é sua", para não revelar quais ids existem.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resultado não encontrado."));
        return analises.detalhe(tentativa, eu.id());
    }

    /** Para o autor acompanhar quem respondeu o quiz dele. */
    @GetMapping("/quizzes/{codigo}/tentativas")
    @Transactional(readOnly = true)
    public List<TentativaResumo> doQuiz(@PathVariable String codigo,
                                        @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return tentativas.doQuiz(doAutor(codigo, eu).getId());
    }

    /** Médias, questões mais erradas, desempenho por pessoa e a lista de tentativas, para o autor analisar. */
    @GetMapping("/quizzes/{codigo}/relatorio")
    @Transactional(readOnly = true)
    public Relatorio relatorio(@PathVariable String codigo,
                               @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return relatorios.de(doAutor(codigo, eu));
    }

    private Quiz doAutor(String codigo, UsuarioLogado eu) {
        Quiz quiz = quizzes.exigir(codigo);
        if (!quiz.getAutor().getId().equals(eu.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só quem criou o quiz pode ver as respostas.");
        }
        return quiz;
    }
}

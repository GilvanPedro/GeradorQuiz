package br.com.quizlab.tentativa;

import br.com.quizlab.conta.UsuarioLogado;
import br.com.quizlab.conta.UsuarioRepository;
import br.com.quizlab.quiz.Alternativa;
import br.com.quizlab.quiz.Questao;
import br.com.quizlab.quiz.Quiz;
import br.com.quizlab.quiz.QuizRepository;
import br.com.quizlab.quiz.TipoQuestao;
import jakarta.validation.Valid;
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

    public TentativaController(TentativaRepository tentativas, QuizRepository quizzes, UsuarioRepository usuarios) {
        this.tentativas = tentativas;
        this.quizzes = quizzes;
        this.usuarios = usuarios;
    }

    /** Recebe as respostas, corrige e já devolve a análise. */
    @PostMapping("/quizzes/{codigo}/tentativas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TentativaDetalhe responder(@PathVariable String codigo, @Valid @RequestBody TentativaRequest pedido,
                                      @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Quiz quiz = quizzes.exigir(codigo);
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

        Tentativa tentativa = tentativas.save(new Tentativa(quiz, usuarios.getReferenceById(eu.id()), pontos,
                quiz.getQuestoes().size(), marcacoes));
        return TentativaDetalhe.de(tentativa, eu.id());
    }

    @GetMapping("/tentativas")
    @Transactional(readOnly = true)
    public List<TentativaResumo> minhas(@RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return tentativas.doUsuario(eu.id());
    }

    /** Quem fez a tentativa vê a própria análise; o autor do quiz vê a de todo mundo que respondeu. */
    @GetMapping("/tentativas/{id}")
    @Transactional(readOnly = true)
    public TentativaDetalhe detalhe(@PathVariable Long id, @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Tentativa tentativa = tentativas.findById(id)
                .filter(t -> t.getUsuario().getId().equals(eu.id()) || t.getQuiz().getAutor().getId().equals(eu.id()))
                // Mesma resposta para "não existe" e "não é sua", para não revelar quais ids existem.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resultado não encontrado."));
        return TentativaDetalhe.de(tentativa, eu.id());
    }

    /** Para o autor acompanhar quem respondeu o quiz dele. */
    @GetMapping("/quizzes/{codigo}/tentativas")
    @Transactional(readOnly = true)
    public List<TentativaResumo> doQuiz(@PathVariable String codigo,
                                        @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Quiz quiz = quizzes.exigir(codigo);
        if (!quiz.getAutor().getId().equals(eu.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só quem criou o quiz pode ver as respostas.");
        }
        return tentativas.doQuiz(quiz.getId());
    }
}

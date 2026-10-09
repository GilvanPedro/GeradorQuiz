package br.com.quizlab.quiz;

import br.com.quizlab.conta.UsuarioLogado;
import br.com.quizlab.conta.UsuarioRepository;
import br.com.quizlab.tentativa.Analises;
import br.com.quizlab.tentativa.TentativaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    /** Sem 0, o, 1, l e i, que se confundem quando alguém dita ou digita o link. */
    private static final String LETRAS_DO_CODIGO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int TAMANHO_DO_CODIGO = 8;
    private static final int MAXIMO_NA_LISTA_PUBLICA = 200;

    private final QuizRepository quizzes;
    private final UsuarioRepository usuarios;
    private final TentativaRepository tentativas;
    private final Analises analises;
    private final SecureRandom sorteio = new SecureRandom();

    public QuizController(QuizRepository quizzes, UsuarioRepository usuarios, TentativaRepository tentativas,
                          Analises analises) {
        this.quizzes = quizzes;
        this.usuarios = usuarios;
        this.tentativas = tentativas;
        this.analises = analises;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<QuizResumo> publicos() {
        return quizzes.publicos(PageRequest.of(0, MAXIMO_NA_LISTA_PUBLICA));
    }

    @GetMapping("/meus")
    @Transactional(readOnly = true)
    public List<QuizResumo> meus(@RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return quizzes.doAutor(eu.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuizCriado criar(@Valid @RequestBody QuizRequest pedido,
                            @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        conferir(pedido);
        Quiz quiz = new Quiz(novoCodigo(), usuarios.getReferenceById(eu.id()));
        quiz.preencher(pedido);
        quizzes.save(quiz);
        return new QuizCriado(quiz.getCodigo());
    }

    /** Qualquer pessoa logada que tenha o link pode abrir, mesmo que o quiz não esteja na lista pública. */
    @GetMapping("/{codigo}")
    @Transactional(readOnly = true)
    public QuizParaResponder abrir(@PathVariable String codigo,
                                   @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        return QuizParaResponder.de(quizzes.exigir(codigo), eu.id());
    }

    @GetMapping("/{codigo}/edicao")
    @Transactional(readOnly = true)
    public QuizParaEditar abrirParaEditar(@PathVariable String codigo,
                                          @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Quiz quiz = doAutor(codigo, eu);
        return QuizParaEditar.de(quiz, !respondidoPorOutros(quiz, eu));
    }

    @PutMapping("/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void editar(@PathVariable String codigo, @Valid @RequestBody QuizRequest pedido,
                       @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        Quiz quiz = doAutor(codigo, eu);
        conferir(pedido);
        // Mudar as questões depois que alguém respondeu deixaria o resultado dessa pessoa sem sentido.
        if (respondidoPorOutros(quiz, eu)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Outras pessoas já responderam este quiz, então as questões não podem mais mudar.");
        }
        // As tentativas do próprio autor são testes: saem junto com as questões antigas.
        tentativas.deleteAll(tentativas.findByQuizIdAndUsuarioId(quiz.getId(), eu.id()));
        tentativas.flush();
        quiz.preencher(pedido);
    }

    @PutMapping("/{codigo}/publico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void mudarVisibilidade(@PathVariable String codigo, @Valid @RequestBody VisibilidadeRequest pedido,
                                  @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        doAutor(codigo, eu).definirPublico(pedido.publico());
    }

    /**
     * Apaga o quiz, as questões e as respostas do banco. Quem já respondeu continua vendo o próprio resultado, a
     * partir de uma cópia da análise guardada na tentativa.
     */
    @DeleteMapping("/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluir(@PathVariable String codigo, @RequestAttribute(UsuarioLogado.ATRIBUTO) UsuarioLogado eu) {
        analises.apagarQuiz(doAutor(codigo, eu).getId());
    }

    private Quiz doAutor(String codigo, UsuarioLogado eu) {
        Quiz quiz = quizzes.exigir(codigo);
        if (!quiz.getAutor().getId().equals(eu.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só quem criou o quiz pode fazer isso.");
        }
        return quiz;
    }

    private boolean respondidoPorOutros(Quiz quiz, UsuarioLogado eu) {
        return tentativas.existsByQuizIdAndUsuarioIdNot(quiz.getId(), eu.id());
    }

    /** Regras que dependem do tipo da questão. */
    private static void conferir(QuizRequest pedido) {
        int numero = 0;
        for (QuizRequest.QuestaoRequest questao : pedido.questoes()) {
            numero++;
            long corretas = questao.alternativas().stream().filter(QuizRequest.AlternativaRequest::correta).count();
            String problema = switch (questao.tipo()) {
                case UNICA -> questao.alternativas().size() < 2 ? "precisa de pelo menos duas alternativas."
                        : corretas != 1 ? "marque exatamente uma alternativa como correta." : null;
                case MULTIPLA -> questao.alternativas().size() < 2 ? "precisa de pelo menos duas alternativas."
                        : corretas < 1 ? "marque pelo menos uma alternativa como correta." : null;
                case VERDADEIRO_FALSO -> null;
            };
            if (problema != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Questão " + numero + ": " + problema);
            }
        }
    }

    private String novoCodigo() {
        while (true) {
            StringBuilder codigo = new StringBuilder(TAMANHO_DO_CODIGO);
            for (int i = 0; i < TAMANHO_DO_CODIGO; i++) {
                codigo.append(LETRAS_DO_CODIGO.charAt(sorteio.nextInt(LETRAS_DO_CODIGO.length())));
            }
            if (!quizzes.existsByCodigo(codigo.toString())) {
                return codigo.toString();
            }
        }
    }

    public record QuizCriado(String codigo) {
    }

    public record VisibilidadeRequest(@NotNull(message = "Informe se o quiz é público.") Boolean publico) {
    }
}

package br.com.quizlab.conta;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Só deixa passar quem mandou um token de login válido, e anota na requisição quem é a pessoa. A exceção é abrir e
 * responder um quiz pelo link, que funciona sem conta: nesses dois casos a requisição segue sem ninguém anotado.
 */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    /** Abrir um quiz pelo código. "meus" fica de fora: é a lista de quizzes de quem está logado. */
    private static final Pattern ABRIR_QUIZ = Pattern.compile("/api/quizzes/(?!meus$)[^/]+");
    private static final Pattern RESPONDER_QUIZ = Pattern.compile("/api/quizzes/[^/]+/tentativas");

    private final Sessoes sessoes;

    public AutenticacaoInterceptor(Sessoes sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // A consulta prévia de CORS do navegador (OPTIONS) nunca leva o token.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = Sessoes.tokenDe(request.getHeader(HttpHeaders.AUTHORIZATION));
        Optional<UsuarioLogado> usuario = sessoes.usuarioDe(token);
        if (usuario.isPresent()) {
            request.setAttribute(UsuarioLogado.ATRIBUTO, usuario.get());
            return true;
        }
        // Um token vencido é recusado mesmo nas rotas abertas, para o site perceber que a sessão acabou em vez
        // de registrar como "sem conta" a resposta de alguém que achava estar logado.
        if (token == null && abertaParaConvidados(request)) {
            return true;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
    }

    private static boolean abertaParaConvidados(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        return HttpMethod.GET.matches(request.getMethod()) && ABRIR_QUIZ.matcher(caminho).matches()
                || HttpMethod.POST.matches(request.getMethod()) && RESPONDER_QUIZ.matcher(caminho).matches();
    }
}

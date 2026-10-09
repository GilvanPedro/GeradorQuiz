package br.com.quizlab.conta;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/** Só deixa passar quem mandou um token de login válido, e anota na requisição quem é a pessoa. */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

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
        UsuarioLogado usuario = sessoes.usuarioDe(Sessoes.tokenDe(request.getHeader(HttpHeaders.AUTHORIZATION)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login para continuar."));
        request.setAttribute(UsuarioLogado.ATRIBUTO, usuario);
        return true;
    }
}

package br.com.quizlab.conta;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Tudo exige login, menos criar conta, entrar, sair e /api/saude. */
@Configuration
public class AutenticacaoConfig implements WebMvcConfigurer {

    private final AutenticacaoInterceptor interceptor;

    public AutenticacaoConfig(AutenticacaoInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/contas", "/api/login", "/api/logout", "/api/saude");
    }
}

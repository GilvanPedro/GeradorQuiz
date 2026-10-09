package br.com.quizlab;

import br.com.quizlab.config.BancoDeDados;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApp {

    public static void main(String[] args) {
        BancoDeDados.configurar(System.getenv("DATABASE_URL"));
        SpringApplication.run(ApiApp.class, args);
    }
}

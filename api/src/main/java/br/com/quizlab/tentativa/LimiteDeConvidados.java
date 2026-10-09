package br.com.quizlab.tentativa;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responder sem conta é aberto a qualquer um que tenha o link, então há um teto de envios por endereço de rede,
 * para ninguém encher o relatório de um quiz com respostas falsas. Fica em memória: zera quando a API reinicia.
 */
@Component
public class LimiteDeConvidados {

    static final int ENVIOS_POR_JANELA = 30;
    static final Duration JANELA = Duration.ofMinutes(10);

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    /** Conta mais um envio deste endereço e diz se ele ainda está dentro do teto. */
    public boolean permite(String endereco) {
        Instant agora = Instant.now();
        if (janelas.size() > 10_000) {
            janelas.values().removeIf(j -> j.inicio.plus(JANELA).isBefore(agora));
        }
        Janela janela = janelas.compute(endereco, (chave, atual) ->
                atual == null || atual.inicio.plus(JANELA).isBefore(agora) ? new Janela(agora, 1)
                        : new Janela(atual.inicio, atual.envios + 1));
        return janela.envios <= ENVIOS_POR_JANELA;
    }

    private record Janela(Instant inicio, int envios) {
    }
}

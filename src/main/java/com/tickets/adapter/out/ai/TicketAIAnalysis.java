package com.tickets.adapter.out.ai;

import com.tickets.domain.exception.ConexionIAException;

import java.util.List;
import java.util.Optional;

/**
 * Fachada de IA: recibe el system prompt y las instrucciones y delega al proveedor configurado
 * (AI_PROVIDER). Agrega reintentos ante errores transitorios.
 */
public class TicketAIAnalysis {

    private final List<AIModelProvider> proveedores;
    private final AIProperties props;

    public TicketAIAnalysis(List<AIModelProvider> proveedores, AIProperties props) {
        this.proveedores = proveedores;
        this.props = props;
    }

    public AIModelProvider.AIRespuesta analyze(String systemPrompt, String instrucciones) {
        AIModelProvider proveedor = proveedores.stream()
                .filter(p -> p.nombre().equalsIgnoreCase(props.provider())).findFirst()
                .orElseThrow(() -> new ConexionIAException(
                        "Proveedor de IA no configurado: '%s'".formatted(props.provider()), false, null));

        ConexionIAException ultimo = null;
        for (int intento = 0; intento <= props.maxRetries(); intento++) {
            try {
                return proveedor.generar(systemPrompt, instrucciones);
            } catch (ConexionIAException e) {
                ultimo = e;
                if (!e.esReintentable() || intento == props.maxRetries()) {
                    break;
                }
                dormir(props.retryBackoffMs() * (intento + 1));
            }
        }
        throw Optional.ofNullable(ultimo).orElseThrow();
    }

    private static void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.tickets.adapter.in.worker;

import com.tickets.application.port.in.ProcesarColaUseCase;
import com.tickets.shared.log.Log;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

/** Worker de la cola de tareas (analisis de IA e indexacion). Consulta la cola periodicamente. */
public class TareasWorker {

    private static final int MAX_POR_CICLO = 20;

    private final ProcesarColaUseCase procesar;

    public TareasWorker(ProcesarColaUseCase procesar) {
        this.procesar = procesar;
    }

    @Scheduled(fixedDelayString = "${analysis.poll-ms:1000}")
    public void ejecutar() {
        try {
            int procesadas = 0;
            while (procesadas < MAX_POR_CICLO && procesar.procesarSiguiente()) {
                procesadas++;
            }
        } catch (RuntimeException e) {
            Log.error("Fallo en el ciclo del worker de tareas", Map.of(), e);
        }
    }
}

package com.tickets.application.service;

import com.tickets.application.port.out.ContadorTasa;

import java.time.Clock;
import java.time.Duration;

/** Limitador de tasa por ventana fija alineada al reloj: simple, barato y compartido entre instancias. */
public class Limitador {

    public record Decision(boolean permitido, int limite, long restantes, long reintentarEnSegundos) {
    }

    private final ContadorTasa contador;
    private final Clock reloj;

    public Limitador(ContadorTasa contador, Clock reloj) {
        this.contador = contador;
        this.reloj = reloj;
    }

    public Decision evaluar(String clave, int limite, Duration ventana) {
        long segundosVentana = Math.max(1, ventana.toSeconds());
        long ahora = reloj.instant().getEpochSecond();
        long numeroDeVentana = ahora / segundosVentana;
        long usadas = contador.incrementar(clave + ":" + numeroDeVentana, ventana);
        long restanteDeVentana = segundosVentana - (ahora % segundosVentana);
        return new Decision(usadas <= limite, limite, Math.max(0, limite - usadas), restanteDeVentana);
    }
}

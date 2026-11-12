package com.tickets.application.port.out;

import java.time.Duration;

/** Contador por clave con ventana fija (para limitar la tasa de peticiones). */
public interface ContadorTasa {
    /** Suma 1 a la clave y devuelve el total dentro de la ventana en curso. */
    long incrementar(String clave, Duration ventana);
}

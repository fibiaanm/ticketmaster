package com.tickets.application.service;

import com.tickets.application.port.out.EjecutorTransaccional;
import com.tickets.domain.exception.ConflictoConcurrenciaException;

import java.util.function.Supplier;

/**
 * Ejecuta un bloque en una transaccion y, si otro proceso modifico el mismo recurso (bloqueo optimista),
 * repite el bloque completo con datos frescos. El reintento va FUERA de la transaccion: una transaccion
 * con conflicto no se puede reutilizar.
 */
public class Transaccion {

    static final int MAX_INTENTOS = 3;

    private final EjecutorTransaccional ejecutor;

    public Transaccion(EjecutorTransaccional ejecutor) {
        this.ejecutor = ejecutor;
    }

    public <T> T conReintento(Supplier<T> bloque) {
        for (int intento = 1; ; intento++) {
            try {
                return ejecutor.ejecutar(bloque);
            } catch (ConflictoConcurrenciaException e) {
                if (intento >= MAX_INTENTOS) {
                    throw e;
                }
            }
        }
    }
}

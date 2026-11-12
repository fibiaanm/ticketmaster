package com.tickets.support;

import com.tickets.application.port.out.EjecutorTransaccional;
import com.tickets.application.service.Transaccion;

import java.util.function.Supplier;

/** Ejecuta el bloque sin transaccion real (los fakes en memoria no la necesitan). */
public class EjecutorDirecto implements EjecutorTransaccional {

    public static Transaccion transaccion() {
        return new Transaccion(new EjecutorDirecto());
    }

    @Override
    public <T> T ejecutar(Supplier<T> bloque) {
        return bloque.get();
    }
}

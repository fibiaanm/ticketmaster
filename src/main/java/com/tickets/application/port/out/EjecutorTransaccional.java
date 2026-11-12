package com.tickets.application.port.out;

import java.util.function.Supplier;

/** Frontera transaccional: todo lo que ocurre dentro del bloque se confirma o se revierte junto. */
public interface EjecutorTransaccional {
    <T> T ejecutar(Supplier<T> bloque);
}

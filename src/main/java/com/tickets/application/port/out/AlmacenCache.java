package com.tickets.application.port.out;

import java.time.Duration;
import java.util.Optional;

/** Almacen clave-valor con expiracion (Valkey). Los fallos del almacen nunca deben romper la operacion. */
public interface AlmacenCache {
    Optional<String> leer(String clave);

    void guardar(String clave, String valor, Duration ttl);

    void eliminar(String clave);
}

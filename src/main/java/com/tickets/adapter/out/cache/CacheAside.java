package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.AlmacenCache;
import com.tickets.shared.log.Log;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Patron cache-aside con serializacion JSON. Un valor ilegible en cache se trata como fallo de cache. */
public class CacheAside {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AlmacenCache almacen;

    public CacheAside(AlmacenCache almacen) {
        this.almacen = almacen;
    }

    /** Devuelve el valor cacheado o lo carga y lo guarda. Un resultado vacio NO se cachea. */
    public <T> Optional<T> obtener(String clave, Duration ttl, Class<T> tipo, Supplier<Optional<T>> cargador) {
        Optional<String> enCache = almacen.leer(clave);
        if (enCache.isPresent()) {
            try {
                return Optional.of(JSON.readValue(enCache.get(), tipo));
            } catch (RuntimeException e) {
                Log.warn("Valor de cache ilegible; se recarga", Map.of("clave", clave));
                almacen.eliminar(clave);
            }
        }
        Optional<T> cargado = cargador.get();
        cargado.ifPresent(v -> almacen.guardar(clave, JSON.writeValueAsString(v), ttl));
        return cargado;
    }

    public <T> T obtenerSiempre(String clave, Duration ttl, Class<T> tipo, Supplier<T> cargador) {
        return obtener(clave, ttl, tipo, () -> Optional.of(cargador.get())).orElseThrow();
    }

    public void eliminar(String clave) {
        almacen.eliminar(clave);
    }
}

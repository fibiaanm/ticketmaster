package com.tickets.adapter.out.cache;

import com.tickets.support.RelojMutable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideTest {

    record Dato(String nombre, int n) { }

    RelojMutable reloj = new RelojMutable(Instant.parse("2026-01-01T00:00:00Z"));
    MemoriaAlmacenCache almacen = new MemoriaAlmacenCache(reloj);
    CacheAside cache = new CacheAside(almacen);
    AtomicInteger cargas = new AtomicInteger();

    Optional<Dato> cargar() { cargas.incrementAndGet(); return Optional.of(new Dato("x", 1)); }

    @Test
    void laSegundaLecturaSaleDeLaCache() {
        assertThat(cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar)).contains(new Dato("x", 1));
        assertThat(cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar)).contains(new Dato("x", 1));
        assertThat(cargas.get()).isEqualTo(1);
    }

    @Test
    void expiraPorTtl() {
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar);
        reloj.avanzar(Duration.ofSeconds(31));
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar);
        assertThat(cargas.get()).isEqualTo(2);
    }

    @Test
    void unResultadoVacioNoSeCachea() {
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, () -> { cargas.incrementAndGet(); return Optional.empty(); });
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, () -> { cargas.incrementAndGet(); return Optional.empty(); });
        assertThat(cargas.get()).isEqualTo(2);
    }

    @Test
    void eliminarForzaLaRecarga() {
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar);
        cache.eliminar("k");
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar);
        assertThat(cargas.get()).isEqualTo(2);
    }

    @Test
    void unValorIlegibleEnCacheSeRecargaEnVezDeFallar() {
        almacen.guardar("k", "{esto no es json", Duration.ofSeconds(30));
        assertThat(cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar)).contains(new Dato("x", 1));
        assertThat(cargas.get()).isEqualTo(1);
        // y queda reparada
        cache.obtener("k", Duration.ofSeconds(30), Dato.class, this::cargar);
        assertThat(cargas.get()).isEqualTo(1);
    }
}

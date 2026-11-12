package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;
import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.application.port.out.SesionRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.PromptTemplate;
import com.tickets.domain.Rol;
import com.tickets.domain.Sesion;
import com.tickets.domain.Usuario;
import com.tickets.support.RelojMutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoriosConCacheTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final Duration TTL = Duration.ofSeconds(60);

    @Mock UsuarioRepository usuarios;
    @Mock SesionRepository sesiones;
    @Mock PromptTemplateRepository prompts;
    @Mock MetricasRepository metricas;

    RelojMutable reloj = new RelojMutable(T0);
    MemoriaAlmacenCache almacen = new MemoriaAlmacenCache(reloj);
    CacheAside cache;

    @BeforeEach
    void setUp() {
        cache = new CacheAside(almacen);
    }

    // ---- usuarios
    @Test
    void elUsuarioPorIdSeCacheaSinElHashDeLaContrasena() {
        Usuario u = new Usuario(1L, "Ana", "ana@x.com", "$2a$10$HASHSECRETO", Rol.SOPORTE, T0);
        when(usuarios.buscarPorId(1L)).thenReturn(Optional.of(u));
        var repo = new UsuarioRepositorioConCache(usuarios, cache, TTL);

        Usuario primero = repo.buscarPorId(1L).orElseThrow();
        Usuario segundo = repo.buscarPorId(1L).orElseThrow();

        verify(usuarios, times(1)).buscarPorId(1L);
        assertThat(segundo.rol()).isEqualTo(Rol.SOPORTE);
        assertThat(segundo.passwordHash()).isEmpty();
        assertThat(almacen.leer("usuario:1").orElseThrow()).doesNotContain("HASHSECRETO");
        assertThat(primero.nombre()).isEqualTo("Ana");
    }

    @Test
    void guardarUnUsuarioExpulsaSuEntrada() {
        Usuario u = new Usuario(1L, "Ana", "ana@x.com", "h", Rol.USUARIO, T0);
        when(usuarios.buscarPorId(1L)).thenReturn(Optional.of(u));
        when(usuarios.guardar(u)).thenReturn(u);
        var repo = new UsuarioRepositorioConCache(usuarios, cache, TTL);

        repo.buscarPorId(1L);
        repo.guardar(u);
        repo.buscarPorId(1L);

        verify(usuarios, times(2)).buscarPorId(1L);
    }

    @Test
    void elLoginPorCorreoNuncaPasaPorLaCache() {
        Usuario u = new Usuario(1L, "Ana", "ana@x.com", "h", Rol.USUARIO, T0);
        when(usuarios.buscarPorCorreo("ana@x.com")).thenReturn(Optional.of(u));
        var repo = new UsuarioRepositorioConCache(usuarios, cache, TTL);

        assertThat(repo.buscarPorCorreo("ana@x.com").orElseThrow().passwordHash()).isEqualTo("h");
        assertThat(almacen.leer("usuario:1")).isEmpty();
    }

    // ---- sesiones
    @Test
    void laSesionSeCacheaYRevocarlaLaExpulsaDeInmediato() {
        Sesion s = new Sesion(1L, "hash", 7L, T0.plusSeconds(3600), false);
        when(sesiones.buscarPorHash("hash")).thenReturn(Optional.of(s));
        var repo = new SesionRepositorioConCache(sesiones, cache, Duration.ofSeconds(30));

        repo.buscarPorHash("hash");
        repo.buscarPorHash("hash");
        verify(sesiones, times(1)).buscarPorHash("hash");

        repo.revocar("hash");
        repo.buscarPorHash("hash");
        verify(sesiones).revocar("hash");
        verify(sesiones, times(2)).buscarPorHash("hash");
    }

    @Test
    void unaSesionInexistenteNoSeCachea() {
        when(sesiones.buscarPorHash("nada")).thenReturn(Optional.empty());
        var repo = new SesionRepositorioConCache(sesiones, cache, Duration.ofSeconds(30));
        repo.buscarPorHash("nada");
        repo.buscarPorHash("nada");
        verify(sesiones, times(2)).buscarPorHash("nada");
    }

    // ---- prompts
    @Test
    void elPromptActivoSeCacheaYSeExpulsaAlCambiarLaActiva() {
        PromptTemplate p = new PromptTemplate(1L, "ticket-analysis", 1, "sys", "usr", true, "h");
        when(prompts.buscarActivo("ticket-analysis")).thenReturn(Optional.of(p));
        var repo = new PromptRepositorioConCache(prompts, cache, TTL);

        repo.buscarActivo("ticket-analysis");
        repo.buscarActivo("ticket-analysis");
        verify(prompts, times(1)).buscarActivo("ticket-analysis");

        repo.activarExclusivamente("ticket-analysis", 2);
        repo.buscarActivo("ticket-analysis");
        verify(prompts, times(2)).buscarActivo("ticket-analysis");
    }

    // ---- metricas
    @Test
    void lasMetricasSeCacheanHastaQueExpiraElTtl() {
        MetricasTickets m = new MetricasTickets(1, Map.of("OTRO", 1L), Map.of("BAJA", 1L), Map.of("ABIERTO", 1L),
                Map.of("COMPLETADO", 1L), List.of(new MetricasTickets.Celda("OTRO", "BAJA", 1)));
        when(metricas.calcular()).thenReturn(m);
        var repo = new MetricasRepositorioConCache(metricas, cache, Duration.ofSeconds(60));

        assertThat(repo.calcular()).isEqualTo(m);
        assertThat(repo.calcular()).isEqualTo(m);
        verify(metricas, times(1)).calcular();

        reloj.avanzar(Duration.ofSeconds(61));
        repo.calcular();
        verify(metricas, times(2)).calcular();
    }
}

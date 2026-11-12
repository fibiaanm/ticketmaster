package com.tickets.adapter.in.web;

import com.tickets.adapter.out.search.OpenSearchCliente;
import com.tickets.adapter.out.search.OpenSearchIndexador;
import com.tickets.application.port.in.ProcesarColaUseCase;
import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo en modo hibrido contra un OpenSearch real: outbox -> worker -> indice -> busqueda. */
class BusquedaHibridaIT extends ApiTestBase {

    static final String INDICE = "tickets-it-" + UUID.randomUUID();
    static final String URL = System.getenv().getOrDefault("OPENSEARCH_URL", "http://localhost:9200");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("search.engine", () -> "hybrid");
        r.add("search.opensearch.url", () -> URL);
        r.add("search.opensearch.index", () -> INDICE);
    }

    @Autowired ProcesarColaUseCase worker;
    @Autowired OpenSearchCliente cliente;
    @Autowired OpenSearchIndexador indexador;

    @BeforeEach
    void prepararIndice() {
        try {
            cliente.enviar("GET", "/_cluster/health", null);
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "OpenSearch no disponible: prueba omitida");
        }
        indexador.recrearIndice();
    }

    /** Procesa todas las tareas pendientes (INDEXAR) y fuerza el refresh del indice. */
    void sincronizar() {
        while (worker.procesarSiguiente()) { /* drenar la cola */ }
        cliente.enviar("POST", "/" + INDICE + "/_refresh", null);
    }

    long crear(String titulo, String descripcion) throws Exception {
        var r = mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson(titulo, descripcion, usuario.id()))).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    @Test
    void cadaCambioSeIndexaPorLaColaYLaBusquedaLoEncuentra() throws Exception {
        long id = crear("Reunión sin conexión", "No puedo conectarme a internet antes de la reunión importante");
        crear("Impresora atascada", "La impresora del segundo piso atasca el papel cada vez que imprimo");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tarea WHERE tipo = 'INDEXAR' AND estado = 'PENDIENTE'", Integer.class))
                .isGreaterThanOrEqualTo(2);
        sincronizar();

        mvc.perform(jwt(get("/api/tickets?q=REUNIONES"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));

        // un cambio de estado se refleja al reindexar; el filtro sin texto sigue yendo a MySQL
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")).andExpect(status().isOk());
        sincronizar();
        mvc.perform(jwt(get("/api/tickets?q=reunion&estado=EN_PROCESO"), soporte)).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(jwt(get("/api/tickets?q=reunion&estado=CERRADO"), soporte)).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void elTextoRespetaElAlcanceDelUsuarioTambienEnOpenSearch() throws Exception {
        crear("Internet uno", "Desde esta mañana no puedo conectarme a internet");
        mvc.perform(jwt(post("/api/tickets"), otroUsuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Internet otro", "Mi internet tambien falla desde hace horas", otroUsuario.id())));
        sincronizar();

        mvc.perform(jwt(get("/api/tickets?q=internet"), usuario))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].solicitante.id").value(usuario.id()));
        mvc.perform(jwt(get("/api/tickets?q=internet"), soporte)).andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void unTicketEliminadoSaleDeLaBusqueda() throws Exception {
        long id = crear("Ticket temporal unico", "Descripcion del ticket temporal unico para borrar");
        sincronizar();
        mvc.perform(jwt(get("/api/tickets?q=temporal"), admin)).andExpect(jsonPath("$.totalElementos").value(1));

        mvc.perform(jwt(delete("/api/tickets/" + id), admin)).andExpect(status().isNoContent());
        // aun antes de reindexar, MySQL descarta el eliminado
        mvc.perform(jwt(get("/api/tickets?q=temporal"), admin)).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
        sincronizar();
        mvc.perform(jwt(get("/api/tickets?q=temporal"), admin)).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void elAdminPuedeReindexarTodoElIndice() throws Exception {
        crear("Reunión sin conexión", "No puedo conectarme a internet antes de la reunión importante");
        crear("Impresora atascada", "La impresora del segundo piso atasca el papel cada vez que imprimo");
        sincronizar();
        indexador.recrearIndice();   // simula un indice perdido
        cliente.enviar("POST", "/" + INDICE + "/_refresh", null);
        mvc.perform(jwt(get("/api/tickets?q=impresora"), admin)).andExpect(jsonPath("$.contenido", hasSize(0)));

        mvc.perform(jwt(post("/api/admin/busqueda/reindexar"), soporte)).andExpect(status().isForbidden());
        mvc.perform(jwt(post("/api/admin/busqueda/reindexar?recrear=true"), admin))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.programadas").value(2));
        sincronizar();
        mvc.perform(jwt(get("/api/tickets?q=impresora"), admin)).andExpect(jsonPath("$.contenido", hasSize(1)));
    }

    @Test
    void siOpenSearchCaeLaBusquedaSigueFuncionandoDesdeMysql() throws Exception {
        crear("Problema con internet", "Desde esta mañana no puedo conectarme a internet");
        // la URL apunta a un OpenSearch real; para simular la caida se borra el indice y se rompe el cliente via 404
        cliente.enviar("DELETE", "/" + INDICE, null, 404);
        mvc.perform(jwt(get("/api/tickets?q=internet"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))      // respondio MySQL (LIKE)
                .andExpect(jsonPath("$.contenido[0].titulo").value("Problema con internet"));
    }
}

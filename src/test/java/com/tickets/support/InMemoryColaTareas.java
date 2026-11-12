package com.tickets.support;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.Tarea;
import com.tickets.application.port.out.TipoTarea;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryColaTareas implements ColaTareas {

    public static class Item {
        public long id; public TipoTarea tipo; public long ticketId; public int intentos; public String requestId;
        public String estado = "PENDIENTE"; public String error; public Instant disponibleDesde = Instant.EPOCH;
    }

    public final List<Item> items = new ArrayList<>();

    @Override
    public void encolar(TipoTarea tipo, Long ticketId, String requestId) {
        Item i = new Item();
        i.id = items.size() + 1; i.tipo = tipo; i.ticketId = ticketId; i.requestId = requestId;
        items.add(i);
    }

    @Override
    public Optional<Tarea> tomarSiguiente() {
        return items.stream().filter(i -> i.estado.equals("PENDIENTE")).findFirst().map(i -> {
            i.estado = "PROCESANDO"; i.intentos++;
            return new Tarea(i.id, i.tipo, i.ticketId, i.intentos, i.requestId);
        });
    }

    private Item item(Long id) { return items.stream().filter(i -> i.id == id).findFirst().orElseThrow(); }

    @Override
    public void completar(Long id) { item(id).estado = "COMPLETADO"; }

    @Override
    public void reprogramar(Long id, String error, Instant disponibleDesde) {
        Item i = item(id); i.estado = "PENDIENTE"; i.error = error; i.disponibleDesde = disponibleDesde;
    }

    @Override
    public void marcarFallida(Long id, String error) { Item i = item(id); i.estado = "FALLIDO"; i.error = error; }
}

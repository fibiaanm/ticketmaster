package com.tickets.application.port.out;

import com.tickets.domain.exception.SolicitudInvalidaException;

import java.util.Arrays;
import java.util.Locale;

/** Orden del listado: "campo" o "campo,asc|desc". Solo campos de una lista blanca. */
public record Orden(Campo campo, Direccion direccion) {

    public enum Campo {
        ID("id"), FECHA_CREACION("fechaCreacion"), FECHA_ACTUALIZACION("fechaActualizacion"),
        PRIORIDAD("prioridad"), TITULO("titulo");

        public final String nombre;

        Campo(String nombre) {
            this.nombre = nombre;
        }
    }

    public enum Direccion { ASC, DESC }

    public static final Orden POR_DEFECTO = new Orden(Campo.FECHA_CREACION, Direccion.DESC);

    /** La prioridad se ordena por gravedad (BAJA < MEDIA < ALTA < CRITICA), no alfabeticamente. */
    public static Orden parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String[] partes = texto.split(",", -1);
        if (partes.length > 2) {
            throw invalido(texto);
        }
        Campo campo = Arrays.stream(Campo.values()).filter(c -> c.nombre.equalsIgnoreCase(partes[0].strip()))
                .findFirst().orElseThrow(() -> invalido(texto));
        Direccion direccion = Direccion.ASC;
        if (partes.length == 2) {
            direccion = switch (partes[1].strip().toLowerCase(Locale.ROOT)) {
                case "asc" -> Direccion.ASC;
                case "desc" -> Direccion.DESC;
                default -> throw invalido(texto);
            };
        }
        return new Orden(campo, direccion);
    }

    private static SolicitudInvalidaException invalido(String texto) {
        return new SolicitudInvalidaException("Orden inválido: '%s'. Use campo[,asc|desc] con campo en %s"
                .formatted(texto, Arrays.stream(Campo.values()).map(c -> c.nombre).toList()));
    }
}

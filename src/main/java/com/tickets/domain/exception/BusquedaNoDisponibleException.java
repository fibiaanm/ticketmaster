package com.tickets.domain.exception;

/** El motor de busqueda externo no respondio o rechazo la consulta. El listado recurre a MySQL. */
public class BusquedaNoDisponibleException extends RuntimeException {
    public BusquedaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

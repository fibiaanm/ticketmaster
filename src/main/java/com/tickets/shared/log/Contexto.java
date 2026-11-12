package com.tickets.shared.log;

import org.slf4j.MDC;

/** Contexto de correlacion (requestId) propagado a los logs, incluso al worker asincrono. */
public final class Contexto {

    public static final String REQUEST_ID = "requestId";

    private Contexto() {
    }

    public static void requestId(String id) {
        if (id == null) {
            MDC.remove(REQUEST_ID);
        } else {
            MDC.put(REQUEST_ID, id);
        }
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static void limpiar() {
        MDC.remove(REQUEST_ID);
    }
}

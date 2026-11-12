package com.tickets.shared.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fachada de logging: {@code Log.info("mensaje", data)}.
 * <p>
 * La data se serializa a STRING (campo {@code data}) para que OpenSearch no la indexe por campo
 * y se eviten problemas de mapeo/ingesta. Los campos sensibles se redactan antes de serializar.
 * {@code critical} se emite como ERROR con {@code severity=CRITICAL} (Logback no tiene ese nivel).
 */
public final class Log {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern SENSIBLE = Pattern.compile(
            "(?i).*(password|passwd|pwd|token|authorization|secret|api[-_]?key|x-ticket-auth|jwt|credential).*");
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private Log() {
    }

    public static void debug(String mensaje) { log(Nivel.DEBUG, mensaje, null, null); }
    public static void debug(String mensaje, Object data) { log(Nivel.DEBUG, mensaje, data, null); }
    public static void info(String mensaje) { log(Nivel.INFO, mensaje, null, null); }
    public static void info(String mensaje, Object data) { log(Nivel.INFO, mensaje, data, null); }
    public static void warn(String mensaje) { log(Nivel.WARN, mensaje, null, null); }
    public static void warn(String mensaje, Object data) { log(Nivel.WARN, mensaje, data, null); }
    public static void error(String mensaje) { log(Nivel.ERROR, mensaje, null, null); }
    public static void error(String mensaje, Object data) { log(Nivel.ERROR, mensaje, data, null); }
    public static void error(String mensaje, Object data, Throwable t) { log(Nivel.ERROR, mensaje, data, t); }
    public static void critical(String mensaje) { log(Nivel.CRITICAL, mensaje, null, null); }
    public static void critical(String mensaje, Object data) { log(Nivel.CRITICAL, mensaje, data, null); }
    public static void critical(String mensaje, Object data, Throwable t) { log(Nivel.CRITICAL, mensaje, data, t); }

    private enum Nivel { DEBUG, INFO, WARN, ERROR, CRITICAL }

    private static void log(Nivel nivel, String mensaje, Object data, Throwable t) {
        Class<?> origen = WALKER.walk(f -> f.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(c -> c != Log.class).findFirst()).orElse(Log.class);
        Logger logger = LoggerFactory.getLogger(origen);
        MDC.put("severity", nivel.name());
        if (data != null) {
            MDC.put("data", aTexto(data));
        }
        try {
            switch (nivel) {
                case DEBUG -> logger.debug(mensaje, t);
                case INFO -> logger.info(mensaje, t);
                case WARN -> logger.warn(mensaje, t);
                case ERROR, CRITICAL -> logger.error(mensaje, t);
            }
        } finally {
            MDC.remove("severity");
            MDC.remove("data");
        }
    }

    static String aTexto(Object data) {
        if (data instanceof Map<?, ?> || data instanceof Iterable<?>) {
            try {
                return MAPPER.writeValueAsString(redactar(data));
            } catch (RuntimeException e) {
                return String.valueOf(redactar(data));
            }
        }
        return String.valueOf(data);
    }

    private static Object redactar(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> copia = new LinkedHashMap<>();
            mapa.forEach((k, v) -> copia.put(String.valueOf(k),
                    SENSIBLE.matcher(String.valueOf(k)).matches() ? "***" : redactar(v)));
            return copia;
        }
        if (valor instanceof Iterable<?> it) {
            List<Object> copia = new ArrayList<>();
            it.forEach(e -> copia.add(redactar(e)));
            return copia;
        }
        return valor;
    }
}

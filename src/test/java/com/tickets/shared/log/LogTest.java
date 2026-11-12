package com.tickets.shared.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogTest {

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setUp() {
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
    }

    ILoggingEvent ultimo() {
        return appender.list.get(appender.list.size() - 1);
    }

    @Test
    void infoRegistraMensajeYDataComoString() {
        Log.info("Ticket creado", Map.of("ticketId", 15));

        ILoggingEvent e = ultimo();
        assertThat(e.getLevel()).isEqualTo(Level.INFO);
        assertThat(e.getFormattedMessage()).isEqualTo("Ticket creado");
        assertThat(e.getMDCPropertyMap().get("data")).isEqualTo("{\"ticketId\":15}");
        assertThat(e.getMDCPropertyMap().get("severity")).isEqualTo("INFO");
        assertThat(e.getLoggerName()).isEqualTo(LogTest.class.getName());
    }

    @Test
    void lasClavesDeLaDataSeLimpianDespuesDeLoguear() {
        Log.info("x", Map.of("a", 1));
        assertThat(MDC.get("data")).isNull();
        assertThat(MDC.get("severity")).isNull();
    }

    @Test
    void sinDataNoAgregaElCampo() {
        Log.warn("solo mensaje");
        assertThat(ultimo().getLevel()).isEqualTo(Level.WARN);
        assertThat(ultimo().getMDCPropertyMap()).doesNotContainKey("data");
    }

    @Test
    void criticalSeEmiteComoErrorConSeverityCritical() {
        Log.critical("Base de datos caída", "detalle");
        assertThat(ultimo().getLevel()).isEqualTo(Level.ERROR);
        assertThat(ultimo().getMDCPropertyMap().get("severity")).isEqualTo("CRITICAL");
    }

    @Test
    void redactaCamposSensibles() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usuario", "ana");
        data.put("password", "pw");
        data.put("Authorization", "Bearer abc");
        data.put("anidado", Map.of("apiKey", "sk-1", "ok", true));
        data.put("lista", List.of(Map.of("token", "t")));

        Log.info("login", data);

        String s = ultimo().getMDCPropertyMap().get("data");
        assertThat(s).contains("\"usuario\":\"ana\"").contains("\"ok\":true");
        assertThat(s).doesNotContain("pw").doesNotContain("Bearer abc").doesNotContain("sk-1").doesNotContain("\"t\"");
        assertThat(s).contains("***");
    }

    @Test
    void errorConExcepcionAdjuntaLaTraza() {
        Log.error("falló", Map.of("x", 1), new IllegalStateException("boom"));
        assertThat(ultimo().getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void objetosArbitrariosSeSerializanConToString() {
        record Dato(int n) {}
        Log.info("rec", new Dato(3));
        assertThat(ultimo().getMDCPropertyMap().get("data")).isEqualTo("Dato[n=3]");
    }
}

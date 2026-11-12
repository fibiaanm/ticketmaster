package com.tickets.adapter.in.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AccessLogIT extends ApiTestBase {

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger acceso = (Logger) LoggerFactory.getLogger("com.tickets.access");

    @BeforeEach
    void conectar() {
        appender.start();
        acceso.addAppender(appender);
    }

    @AfterEach
    void desconectar() {
        acceso.detachAppender(appender);
    }

    @Test
    void registraMetodoRutaStatusDuracionUsuarioYRolSinExponerCredenciales() throws Exception {
        String token = login(admin).path("accessToken").asString();
        appender.list.clear();

        mvc.perform(get("/api/tickets/15").header("Authorization", "Bearer " + token).header("X-Request-Id", "req-acceso-001"));

        ILoggingEvent e = appender.list.get(0);
        var mdc = e.getMDCPropertyMap();
        assertThat(mdc).containsEntry("http_method", "GET").containsEntry("http_path", "/api/tickets/15")
                .containsEntry("http_status", "404").containsEntry("user_rol", "ADMIN")
                .containsEntry("requestId", "req-acceso-001").containsEntry("severity", "WARN");
        assertThat(mdc.get("user_id")).isEqualTo(String.valueOf(admin.id()));
        assertThat(Long.parseLong(mdc.get("duration_ms"))).isGreaterThanOrEqualTo(0);
        assertThat(e.getFormattedMessage() + mdc).doesNotContain(token);
    }

    @Test
    void registraTambienLasPeticionesNoAutenticadas() throws Exception {
        mvc.perform(get("/api/tickets"));
        ILoggingEvent e = appender.list.get(0);
        assertThat(e.getMDCPropertyMap()).containsEntry("http_status", "401").doesNotContainKey("user_id");
    }
}

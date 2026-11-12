package com.tickets.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestDb {

    private TestDb() {
    }

    public static void limpiar(JdbcTemplate jdbc) {
        jdbc.execute("DELETE FROM sesion");
        jdbc.execute("DELETE FROM tarea");
        jdbc.execute("DELETE FROM ticket_evento");
        jdbc.execute("DELETE FROM ticket");
        jdbc.execute("DELETE FROM prompt_template");
        jdbc.execute("DELETE FROM usuario");
    }
}

-- Historial de cambios y auditoria: un evento por cada accion relevante sobre un ticket.
CREATE TABLE ticket_evento (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT       NOT NULL,
    tipo        VARCHAR(30)  NOT NULL,
    actor_id    BIGINT       NULL,
    actor_rol   VARCHAR(20)  NULL,
    request_id  VARCHAR(64)  NULL,
    detalle     TEXT         NULL,
    fecha       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_evento_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT ck_evento_tipo CHECK (tipo IN ('TICKET_CREADO','ESTADO_CAMBIADO','TECNICO_ASIGNADO',
        'TICKET_ELIMINADO','ANALISIS_COMPLETADO','ANALISIS_FALLIDO','REANALISIS_SOLICITADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_evento_ticket ON ticket_evento (ticket_id, id);
CREATE INDEX idx_evento_tipo_fecha ON ticket_evento (tipo, fecha);
CREATE INDEX idx_evento_actor ON ticket_evento (actor_id, fecha);

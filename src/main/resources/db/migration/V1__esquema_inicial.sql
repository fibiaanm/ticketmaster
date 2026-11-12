CREATE TABLE usuario (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nombre          VARCHAR(100) NOT NULL,
    correo          VARCHAR(150) NOT NULL,
    password        VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    fecha_creacion  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_correo UNIQUE (correo),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'SOPORTE', 'USUARIO'))
) ENGINE = InnoDB;

CREATE TABLE prompt_template (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    nombre         VARCHAR(100) NOT NULL,
    version        INT          NOT NULL,
    system_prompt  TEXT         NOT NULL,
    user_template  TEXT         NOT NULL,
    activo         BOOLEAN      NOT NULL DEFAULT FALSE,
    hash           CHAR(64)     NOT NULL,
    fecha_creacion DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_prompt_nombre_version UNIQUE (nombre, version)
) ENGINE = InnoDB;

CREATE TABLE ticket (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    titulo                VARCHAR(100) NOT NULL,
    descripcion           TEXT         NOT NULL,
    categoria             VARCHAR(20)  NULL,
    prioridad             VARCHAR(10)  NULL,
    estado                VARCHAR(20)  NOT NULL,
    usuario_solicitante_id BIGINT      NOT NULL,
    tecnico_asignado_id   BIGINT       NULL,
    resumen_ia            VARCHAR(500) NULL,
    etiquetas_ia          VARCHAR(300) NULL,
    estado_analisis       VARCHAR(15)  NOT NULL,
    analizado_por         VARCHAR(10)  NULL,
    modelo_ia             VARCHAR(100) NULL,
    prompt_template_id    BIGINT       NULL,
    fecha_creacion        DATETIME(6)  NOT NULL,
    fecha_actualizacion   DATETIME(6)  NOT NULL,
    eliminado_en          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_solicitante FOREIGN KEY (usuario_solicitante_id) REFERENCES usuario (id),
    CONSTRAINT fk_ticket_tecnico FOREIGN KEY (tecnico_asignado_id) REFERENCES usuario (id),
    CONSTRAINT fk_ticket_prompt FOREIGN KEY (prompt_template_id) REFERENCES prompt_template (id),
    CONSTRAINT ck_ticket_categoria CHECK (categoria IS NULL OR categoria IN ('HARDWARE','SOFTWARE','CONECTIVIDAD','ACCESOS','OTRO')),
    CONSTRAINT ck_ticket_prioridad CHECK (prioridad IS NULL OR prioridad IN ('BAJA','MEDIA','ALTA','CRITICA')),
    CONSTRAINT ck_ticket_estado CHECK (estado IN ('ABIERTO','EN_PROCESO','RESUELTO','CERRADO')),
    CONSTRAINT ck_ticket_estado_analisis CHECK (estado_analisis IN ('PENDIENTE','ANALIZANDO','COMPLETADO','FALLIDO'))
) ENGINE = InnoDB;

-- Filtros del listado: los compuestos arrancan por la columna mas selectiva/usada.
CREATE INDEX idx_ticket_solicitante ON ticket (usuario_solicitante_id, eliminado_en);
CREATE INDEX idx_ticket_estado_prioridad ON ticket (estado, prioridad, eliminado_en);
CREATE INDEX idx_ticket_categoria ON ticket (categoria, eliminado_en);
CREATE INDEX idx_ticket_estado_analisis ON ticket (estado_analisis);
CREATE INDEX idx_ticket_tecnico ON ticket (tecnico_asignado_id);

CREATE TABLE analisis_job (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id         BIGINT       NOT NULL,
    estado            VARCHAR(15)  NOT NULL,
    intentos          INT          NOT NULL DEFAULT 0,
    ultimo_error      VARCHAR(500) NULL,
    request_id        VARCHAR(64)  NULL,
    disponible_desde  DATETIME(6)  NOT NULL,
    fecha_creacion    DATETIME(6)  NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT ck_job_estado CHECK (estado IN ('PENDIENTE','PROCESANDO','COMPLETADO','FALLIDO'))
) ENGINE = InnoDB;

CREATE INDEX idx_job_cola ON analisis_job (estado, disponible_desde, id);

CREATE TABLE sesion (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash      CHAR(64)    NOT NULL,
    usuario_id      BIGINT      NOT NULL,
    expira_en       DATETIME(6) NOT NULL,
    revocada        BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_creacion  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sesion_token UNIQUE (token_hash),
    CONSTRAINT fk_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB;

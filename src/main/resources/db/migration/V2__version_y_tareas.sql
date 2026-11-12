-- Bloqueo optimista del ticket.
ALTER TABLE ticket ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- La cola de analisis pasa a ser una cola de tareas generica (ANALIZAR, INDEXAR).
RENAME TABLE analisis_job TO tarea;
ALTER TABLE tarea ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'ANALIZAR';
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_tipo CHECK (tipo IN ('ANALIZAR', 'INDEXAR'));
DROP INDEX idx_job_cola ON tarea;
CREATE INDEX idx_tarea_cola ON tarea (estado, disponible_desde, id);

# Bitácora — Sistema Inteligente de Gestión de Tickets

Inicio: 2026-11-12. Metodología: planeación → TDD (rojo/verde/refactor) → bitácora por fase.

---

# Decisiones de diseño

## D1. Arquitectura hexagonal (puertos y adaptadores)
- `domain` puro (sin Spring/JPA), `application` (puertos in/out + servicios de casos de uso), `adapter` (web, worker, persistence, ai, queue), `config`, `security`.
- Mapeo con la estructura sugerida en el enunciado (controller/service/repository/entity/dto/mapper/exception/config/ai/security).
- Los controllers nunca devuelven entidades JPA; solo DTOs.

## D2. Stack y entorno
- Java 21, Spring Boot (última estable compatible al crear el scaffold), Spring Web, Spring Data JPA, MySQL, Maven.
- Todo corre en Docker (incluido Maven vía contenedor con volumen de caché `~/.m2`); no se instala JDK/Maven local.
- Servicios en docker-compose: `mysql`, `app`, `opensearch`, `opensearch-dashboards`, `fluent-bit`, `maven` (herramienta).
- Tests de integración contra el MySQL del compose; Testcontainers queda como mejora opcional (requiere montar el socket de Docker en el contenedor de Maven).

## D3. Análisis con IA: asíncrono por cola, con modo sync configurable
- El análisis no debe ralentizar el endpoint: el ticket se guarda con `estadoAnalisis=PENDIENTE`, se encola, y un worker lo procesa (`ANALIZANDO` → `COMPLETADO` | `FALLIDO`).
- `ANALYSIS_MODE=sync|async` (patrón Strategy): ambas estrategias ejecutan el mismo caso de uso `AnalizarTicket`. Permite cambiar rápido según la latencia del modelo.
- `estadoAnalisis` es un campo separado de `estado` (ABIERTO/EN_PROCESO/RESUELTO/CERRADO) porque son ciclos de vida distintos. Categoría y prioridad quedan nulas hasta completar el análisis.
- Cola: tabla en MySQL con `SELECT ... FOR UPDATE SKIP LOCKED` y reintentos. Sobrevive a reinicios y no añade infraestructura. Queda detrás del puerto `ColaAnalisis` para poder cambiarla por RabbitMQ u otro broker.
- Índices: `estado_analisis`, y compuestos para los filtros del enunciado (`estado, prioridad`, `categoria`, `usuario_solicitante_id`), más `eliminado_en`.

## D4. Fachada `TicketAIAnalysis` + estrategia por proveedor
- `TicketAIAnalysis.analyze(systemPrompt, instrucciones)` (Facade) delega a un `AIModelProvider` (Strategy) elegido por `AI_PROVIDER` y `AI_MODEL`.
- Proveedor inicial: OpenAI, vía HTTP simple con salida JSON. Añadir otro proveedor = una clase nueva.
- `AnalizadorConFallback`: intenta LLM (con timeout y reintentos); si falla por conexión o respuesta inválida, usa `ReglasAnalizador` (Opción B del enunciado, por palabras clave).
- Cada ticket registra `analizadoPor` (LLM | REGLAS), modelo y versión de prompt.
- `AI_API_KEY` en `.env`, fuera de git.

## D5. Prompt manager con versiones
- Tabla `prompt_template` (nombre, versión inmutable, contenido, activo, hash) para trazabilidad.
- Los archivos `resources/prompts/*.txt` son la fuente en el repo y se cargan a la BD al arrancar si esa versión no existe. En runtime se lee de la BD.
- Cada ticket guarda el `prompt_template_id` con el que se analizó.

## D6. Autenticación y autorización
- `POST /api/auth/login` devuelve un JWT y un token de sesión opaco (guardado en BD).
- Un filtro acepta `Authorization: Bearer <jwt>` o `X-Ticket-Auth: <sesion>`; ambos producen el mismo `Principal` con su rol.
- Roles: USUARIO crea y ve solo sus tickets; SOPORTE ve todos, cambia estado y puede ser asignado; ADMIN además asigna técnicos y elimina.
- Seeders (solo perfil `dev`): usuarios ADMIN, SOPORTE y USUARIO, cada uno con su rol y contraseña `pw` (BCrypt). Correos `admin@tickets.local`, `soporte@tickets.local`, `usuario@tickets.local`.

## D7. Borrado lógico
- `DELETE /api/tickets/{id}` marca `eliminado_en`; los listados y consultas excluyen eliminados. Conserva trazabilidad y permite recuperación.

## D8. Logs a OpenSearch
- Logs JSON estructurados de aplicación (INFO/WARN/ERROR) y de acceso HTTP (método, ruta, status, duración, `requestId`, usuario/rol). `requestId` viaja hasta el worker.
- Logback no tiene CRITICAL: se modela como ERROR + campo `severity=CRITICAL`.
- API de log tipo `Log.info("mensaje", data)`. El `data` se **serializa a string** antes de enviarse, para que OpenSearch no lo indexe por campo: así se evitan problemas de ingesta y de explosión de mapeos. El index template lo define como texto no indexado.
- Antes de serializar se redactan campos sensibles (passwords, tokens, headers de autenticación).
- Pipeline: la app escribe a un volumen compartido → Fluent Bit → OpenSearch → Dashboards. Volumen compartido en vez de leer logs de Docker por mayor fiabilidad en Docker Desktop (Mac). Índices diarios con retención.

## D9. Caché de listados: evaluada y NO implementada
- Se evaluó cachear los endpoints de listado (cache-aside con invalidación por versión de namespace).
- Se descartó porque los tickets son datos muy mutables: cada creación, cambio de estado, asignación, borrado y finalización del análisis (que añade campos de IA) invalidaría la caché. El costo de invalidar y de mantener claves por filtros y por alcance de usuario (un USUARIO solo ve los suyos) supera el beneficio. Una caché aporta más en catálogos de elementos casi fijos.
- El enunciado no la pide. Los índices de D3 cubren el rendimiento de los filtros.
- Si en el futuro se adopta una caché, se elegiría **Valkey** y no Redis, porque Redis cambió su licencia a una no open source, mientras que Valkey se mantiene bajo licencia BSD (fork con respaldo de la Linux Foundation) y es compatible con el protocolo de Redis.

---

## D10. Búsqueda híbrida: MySQL + OpenSearch
- MySQL resuelve filtros, orden y paginación; OpenSearch solo la búsqueda de texto `q` (relevancia, tildes, plurales, erratas).
  Con `q` la consulta completa (texto + filtros + fechas + orden) va a OpenSearch; los datos se leen de MySQL por id.
- Razones para no usar solo OpenSearch hoy: consistencia eventual, doble escritura sin transacción, punto único de fallo,
  no es fuente de verdad y el criterio de la prueba evalúa filtros sobre JPA.
- Soporte parcial a propósito: el adaptador de OpenSearch ya implementa TODOS los filtros y el orden; migrar a "solo
  OpenSearch" cuando los datos escalen es cambiar `SEARCH_ENGINE`. `BuscadorEnrutador` decide el motor y recurre a MySQL si falla.
- Indexación con outbox: cada cambio encola una tarea `INDEXAR` en la misma transacción; reindexado completo disponible.

## D11. Valkey: caché y rate limit (reemplaza la decisión de D9 solo en lo que sí tiene valor)
- Valkey y no Redis: Redis cambió a una licencia no open source; Valkey es fork BSD de la Linux Foundation, compatible.
- Se cachean datos de lectura frecuente y poco mutable: métricas, sesión por token, usuario por id (sin hash de
  contraseña) y prompt activo. El listado de tickets sigue SIN caché (ver D9).
- Rate limit de ventana fija compartido entre instancias; degrada a contador local si Valkey cae.

## D12. Concurrencia: `@Version` en vez de un lock en Valkey
- El análisis dura segundos; no se mantiene el ticket cargado: se relee y se aplica solo el resultado, con bloqueo
  optimista y reintento fuera de la transacción. Un lock distribuido expira por tiempo (la llamada dura segundos), no
  protege a quien no lo pida y añade un punto de fallo. Un lock sí encajaría en tareas únicas entre instancias.

## D13. Historial, auditoría, métricas y reanálisis
- `ticket_evento`: un evento por acción (creado, estado, asignación, eliminado, análisis completado/fallido, reanálisis),
  con actor (nulo = sistema), `requestId` y detalle antes/después. El análisis guarda modelo, origen y versión de prompt
  aquí y no en la respuesta del ticket.
- Métricas con GROUP BY en MySQL (cacheadas). Reanálisis solo desde COMPLETADO/FALLIDO (ADMIN).

## D14. Contrato de respuesta
- Campos y nombres tal como la sección 17 del enunciado (`solicitante {id, nombre}`, `tecnicoAsignado`, `resumenIA`,
  `etiquetasIA`, fecha sin zona), más `fechaActualizacion` y `estadoAnalisis` (necesario con el análisis asíncrono).

## D15. Dos suites de pruebas
- Unitarias (`*Test`, surefire, ~4 s, sin infraestructura) e integración (`*IT`, failsafe, ~1 min, servicios reales).
  Se corrigió un error de proceso: los `*IT` no corrían con `mvn test` y solo se ejecutaban nombrándolos.

## D16. Interfaz web mínima servida por Spring
- Página estática sin framework ni build en `src/main/resources/static`, servida por la misma API (mismo origen: sin
  CORS, sin contenedor nuevo). Login, listado con filtros, detalle con análisis de IA y línea de tiempo, alta y métricas.
  Sin acciones de administración ni auditoría: "lo más básico y sencillo posible".
- Seguridad: texto en vez de HTML (anti-XSS), CSP estricta solo para la página (Swagger necesita scripts propios), token
  en `sessionStorage`, rutas estáticas públicas y API protegida. Los accesos a la página no se registran en los logs.

---

# Implementación

Método por fase: tests primero (rojo) → código mínimo (verde) → refactor. Maven siempre en Docker.

- **Fase 0 — Scaffold.** Initializr devolvió `4.1.2.RELEASE`, pero en Maven Central la versión es `4.1.2` (corregido).
  Boot 4 / Spring Security 7 / Hibernate 7 / Jackson 3 (`tools.jackson`). Nimbus no está gestionado por el BOM: se fijó
  la versión (10.10). Compose: mysql, valkey, app, opensearch, dashboards, fluent-bit, inits y maven.
- **Fase 1 — Dominio.** `Ticket` (agregado con reglas: transiciones, asignación solo a SOPORTE, análisis, borrado
  lógico, visibilidad, reanálisis), enums, `EnumParser`. Fachada `Log` con `data` como string y redacción de secretos.
- **Fase 2 — Prompt manager.** `PromptTemplate` versionado e inmutable (hash SHA-256), `SincronizarPromptsService`.
- **Fase 3 — IA.** `ReglasAnalizador` (Opción B, refactorizado a reglas como datos), `RespuestaIAParser`, fachada
  `TicketAIAnalysis`, `OpenAIProvider` (probado con `HttpServer` local), `LlmAnalizador`, `AnalizadorConFallback`.
- **Fase 4 — Persistencia.** Migraciones V1–V3 (FK, CHECKs de enums, índices, `version`, cola de tareas, eventos).
  Bug: leer `proxy.id` (campo) de un `getReference` lazy devuelve null → se usa getter.
- **Fase 5 — Casos de uso.** `TicketService`, `AnalizarTicketService`, `ProcesarColaService` (despacha por tipo de
  tarea), estrategias `Sync/AsyncDisparadorAnalisis`, frontera transaccional como puerto (`EjecutorTransaccional`) y
  reintento por conflicto fuera de la transacción.
- **Fase 6/7/8 — Web.** Controllers, DTO, `@RestControllerAdvice` (formato `timestamp/status/error/message/path`),
  filtros, `sort`, `desde/hasta`, `q`, paginación, estado, asignación, borrado lógico.
- **Fase 9 — Seguridad.** Login (JWT HS256 + sesión opaca con hash SHA-256), filtro `Bearer`/`X-Ticket-Auth`, roles en
  los servicios, login de costo constante, seeders `dev`.
- **Fase 10 — Logs.** Filtro de acceso con `requestId`, Fluent Bit → OpenSearch con index template (`data` = texto no
  indexado) e ISM de 14 días; el index pattern de Dashboards se crea solo.
- **Fase 11 — Docs.** Swagger con los dos esquemas de autenticación, README, diagramas generados con Python.
- **Fase 12 — Base transaccional y contrato.** `@Version`, outbox atómico, cola genérica, formato exacto de la sección
  17, variables de entorno con los nombres del enunciado.
- **Fase 13 — Historial, auditoría, métricas, reanálisis.** `ticket_evento`, `HistorialService`, `MetricasService`.
- **Fase 14 — Valkey.** `AlmacenCache`/`ContadorTasa`, decoradores cacheados (@Primary sobre los adaptadores JPA),
  `RateLimitFilter` después de la autenticación (limita por usuario o IP).
- **Fase 15 — OpenSearch.** Mapeo con analizador de español y normalizador, `ConsultaOpenSearch` (todos los filtros y
  orden), indexador, buscador con lectura fresca desde MySQL, `BuscadorEnrutador`, reindexado.
- **Fase 17 — Interfaz web.** Estáticos + regla de seguridad + CSP; verificada con capturas reales de cada vista.
- **Fase 16 — Calidad y entrega.** Tests con Mockito y `@WebMvcTest`, suites separadas, CI en GitHub Actions,
  `.gitignore` verificado.

# Resultados

- **Pruebas:** 271 tests, 0 fallos, 0 omitidos. Unitarias: 177 en ~3,4 s (probadas sin red). Integración: 94 en ~53 s
  contra MySQL, Valkey y OpenSearch reales. Cubren los 5 mínimos del enunciado (crear, clasificación con IA, ticket
  inexistente, técnico inválido, cambio de estado).
- **Verificación end to end real (stack en Docker):** login con seeders → `POST /api/tickets` → `PENDIENTE` → el worker
  lo completa. Con `AI_API_KEY` real: `gpt-5.4-mini` clasificó "Servidor de producción caído" como `SOFTWARE`/`CRITICA`
  con resumen y etiquetas en ~20 s. En OpenSearch Dashboards: logs con el mismo `requestId` en el `POST` y en el worker.
- **Lecciones:** (1) los `*IT` no corrían con `mvn test` y por eso un conteo anterior de tests era engañoso: se separaron
  las suites; (2) las versiones de prompt inmutables abortaron el arranque cuando un test dejó basura en la BD: los
  tests limpian antes y después; (3) un worker siempre activo compite entre contextos de prueba: en test el sondeo está
  desactivado y los tests lo invocan a mano; (4) Boot 4 cambió paquetes de test y Jackson.
- **Primer CI en GitHub:** falló una prueba de integración (`RateLimitIT`) por una carrera que en local no aparecía: el
  worker de fondo de un contexto de Spring recién arrancado procesaba tareas del test anterior e insertaba eventos
  mientras el siguiente test limpiaba la BD (violación de clave foránea). Solución: el worker se desactiva en las
  pruebas (`analysis.worker.enabled=false`), que invocan la cola a mano. La suite pasó dos veces seguidas en local.
- **Interfaz:** un paréntesis de más en el JavaScript rompió la página y ningún test lo detectó; se añadió `node --check`
  al CI y la verificación visual de cada vista.
- **Limitaciones conocidas:** ver README.

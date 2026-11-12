"""Genera los diagramas del README (arquitectura y modelo de datos).

Uso (no requiere instalar nada en la maquina, solo Docker):
    make diagramas
"""
from pathlib import Path

from graphviz import Digraph

SALIDA = Path(__file__).resolve().parent.parent  # carpeta docs/
FUENTE = "Helvetica"


def base(nombre: str, rankdir: str = "LR") -> Digraph:
    g = Digraph(nombre, format="png")
    g.attr(rankdir=rankdir, fontname=FUENTE, bgcolor="white", pad="0.4", nodesep="0.35", ranksep="0.9",
           dpi="150", compound="true")
    g.attr("node", fontname=FUENTE, fontsize="11", shape="box", style="rounded,filled", color="#5b6b7b")
    g.attr("edge", fontname=FUENTE, fontsize="9", color="#5b6b7b")
    return g


def arquitectura() -> None:
    g = base("arquitectura")

    g.node("cliente", "Cliente\n(Swagger / curl / app)", fillcolor="#eef2f6")

    with g.subgraph(name="cluster_in") as c:
        c.attr(label="adapter/in  (entrada)", style="rounded", color="#2f6f9f", fontname=FUENTE)
        c.node("web", "REST controllers + DTO\n@RestControllerAdvice", fillcolor="#dbeaf7")
        c.node("filtros", "Filtros: RequestLogging → Auth\n(JWT | X-Ticket-Auth) → RateLimit", fillcolor="#dbeaf7")
        c.node("worker", "TareasWorker\n(cola de tareas)", fillcolor="#dbeaf7")

    with g.subgraph(name="cluster_app") as c:
        c.attr(label="application", style="rounded", color="#2f8f5f", fontname=FUENTE)
        c.node("casos", "Casos de uso (port/in)\nTicketService · Analizar · Historial\nMétricas · Reindexar · Autenticación",
               fillcolor="#dcf2e5")
        c.node("puertos", "Puertos de salida (port/out)\nTicketRepository · BuscadorTickets\nAnalizadorTicketService · ColaTareas\nAlmacenCache · RegistroEventos …",
               fillcolor="#dcf2e5")

    g.node("dominio", "domain (puro, sin Spring)\nTicket · Usuario · EventoTicket\nEstados, reglas y excepciones", fillcolor="#fff4d6")

    with g.subgraph(name="cluster_out") as c:
        c.attr(label="adapter/out  (salida)", style="rounded", color="#9f5f2f", fontname=FUENTE)
        c.node("jpa", "JPA / Flyway\nMySQL (fuente de la verdad)\n@Version · cola de tareas · eventos", fillcolor="#f7e6d8")
        c.node("cache", "Valkey\ncaché + rate limit", fillcolor="#f7e6d8")
        c.node("os", "OpenSearch\nbúsqueda de texto (q)", fillcolor="#f7e6d8")
        c.node("ia", "Fachada TicketAIAnalysis\nOpenAI → (fallo) → reglas locales", fillcolor="#f7e6d8")
        c.node("logs", "Logs JSON → Fluent Bit\n→ OpenSearch Dashboards", fillcolor="#f7e6d8")

    g.edge("cliente", "filtros", label="HTTP")
    g.edge("filtros", "web")
    g.edge("web", "casos")
    g.edge("worker", "casos", label="ANALIZAR / INDEXAR")
    g.edge("casos", "dominio")
    g.edge("casos", "puertos")
    for destino, etiqueta in (("jpa", ""), ("cache", ""), ("os", ""), ("ia", "")):
        g.edge("puertos", destino, label=etiqueta, style="dashed")
    g.edge("filtros", "logs", style="dotted", label="acceso HTTP")
    g.edge("casos", "logs", style="dotted", label="Log.info(msg, data)")

    g.render(filename="arquitectura", directory=str(SALIDA), cleanup=True)


def fila(nombre: str, tipo: str, nota: str = "", pk: bool = False) -> str:
    n = f"<B>{nombre}</B>" if pk else nombre
    t = f'<FONT COLOR="#5b6b7b">{tipo}</FONT>' if tipo else " "
    o = f'<FONT COLOR="#9f2f2f">{nota}</FONT>' if nota else " "
    return f'<TR><TD ALIGN="LEFT">{n}</TD><TD ALIGN="LEFT">{t}</TD><TD ALIGN="LEFT">{o}</TD></TR>'


def tabla(g: Digraph, id_: str, titulo: str, color: str, filas: list[str]) -> None:
    html = f'<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4">' \
           f'<TR><TD COLSPAN="3" BGCOLOR="{color}"><B>{titulo}</B></TD></TR>{"".join(filas)}</TABLE>>'
    g.node(id_, label=html, shape="plaintext", style="")


def modelo_datos() -> None:
    g = base("modelo_datos", rankdir="LR")

    tabla(g, "usuario", "usuario", "#dbeaf7", [
        fila("id", "BIGINT", "PK", True), fila("nombre", "VARCHAR(100)"), fila("correo", "VARCHAR(150)", "UNIQUE"),
        fila("password", "BCrypt"), fila("rol", "ADMIN|SOPORTE|USUARIO"), fila("fecha_creacion", "DATETIME")])
    tabla(g, "ticket", "ticket", "#dcf2e5", [
        fila("id", "BIGINT", "PK", True), fila("version", "BIGINT", "bloqueo optimista"),
        fila("titulo / descripcion", "VARCHAR / TEXT"),
        fila("categoria / prioridad", "enum (IA)", "nulos hasta analizar"), fila("estado", "ABIERTO…CERRADO"),
        fila("usuario_solicitante_id", "FK → usuario"), fila("tecnico_asignado_id", "FK → usuario"),
        fila("resumen_ia / etiquetas_ia", "salida de la IA"),
        fila("estado_analisis", "PENDIENTE…FALLIDO", "indexado"), fila("analizado_por / modelo_ia", "LLM|REGLAS"),
        fila("prompt_template_id", "FK → prompt_template"),
        fila("fecha_creacion / actualizacion", "DATETIME"), fila("eliminado_en", "borrado lógico")])
    tabla(g, "evento", "ticket_evento  (historial + auditoría)", "#fff4d6", [
        fila("id", "BIGINT", "PK", True), fila("ticket_id", "FK → ticket"), fila("tipo", "ESTADO_CAMBIADO, …"),
        fila("actor_id / actor_rol", "NULL = sistema"), fila("request_id", "correlación con logs"),
        fila("detalle", "JSON antes/después"), fila("fecha", "DATETIME")])
    tabla(g, "tarea", "tarea  (cola)", "#f7e6d8", [
        fila("id", "BIGINT", "PK", True), fila("tipo", "ANALIZAR|INDEXAR"), fila("ticket_id", "FK → ticket"),
        fila("estado / intentos", "PENDIENTE…FALLIDO"), fila("disponible_desde", "reintentos con espera"),
        fila("request_id", "correlación")])
    tabla(g, "prompt", "prompt_template", "#eadcf2", [
        fila("id", "BIGINT", "PK", True), fila("nombre + version", "UNIQUE, inmutable"),
        fila("system_prompt / user_template", "TEXT"), fila("activo / hash", "una versión activa")])
    tabla(g, "sesion", "sesion", "#dbeaf7", [
        fila("id", "BIGINT", "PK", True), fila("token_hash", "SHA-256", "UNIQUE"), fila("usuario_id", "FK → usuario"),
        fila("expira_en / revocada", "")])

    g.edge("usuario", "ticket", label="1 ── N  solicitante", arrowhead="crow")
    g.edge("usuario", "ticket", label="1 ── N  técnico asignado", arrowhead="crow", style="dashed")
    g.edge("ticket", "evento", label="1 ── N", arrowhead="crow")
    g.edge("ticket", "tarea", label="1 ── N", arrowhead="crow")
    g.edge("prompt", "ticket", label="1 ── N  (trazabilidad)", arrowhead="crow")
    g.edge("usuario", "sesion", label="1 ── N", arrowhead="crow")

    g.render(filename="modelo-datos", directory=str(SALIDA), cleanup=True)


if __name__ == "__main__":
    arquitectura()
    modelo_datos()
    print("Diagramas generados en", SALIDA)

.PHONY: up down test-unit test-integration test logs diagramas

up:
	docker compose up -d --build

down:
	docker compose down

# Rapida: solo pruebas unitarias (*Test). No necesita MySQL, Valkey ni OpenSearch.
test-unit:
	docker compose run --rm --no-deps maven mvn -B -q test

# Integracion (*IT): levanta MySQL, Valkey y OpenSearch del compose. Incluye la unitaria.
test-integration:
	docker compose run --rm maven mvn -B verify

test: test-integration

logs:
	docker compose logs -f app

# Regenera docs/arquitectura.png y docs/modelo-datos.png (Python + Graphviz en un contenedor).
diagramas:
	docker run --rm -v "$(CURDIR)/docs:/docs" python:3.12-slim sh -c \
	  "apt-get update -qq && apt-get install -y -qq graphviz >/dev/null && pip install -q graphviz && python /docs/diagramas/generar.py"

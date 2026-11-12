#!/bin/sh
# Crea (idempotente) el index pattern tickets-logs-* y lo deja como predeterminado.
set -e
D=http://opensearch-dashboards:5601
until curl -sf $D/api/status >/dev/null; do echo "esperando dashboards..."; sleep 3; done
curl -s -o /dev/null -w "index-pattern: HTTP %{http_code}\n" -X POST "$D/api/saved_objects/index-pattern/tickets-logs?overwrite=true" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"attributes":{"title":"tickets-logs-*","timeFieldName":"@timestamp"}}'
curl -s -o /dev/null -w "default: HTTP %{http_code}\n" -X POST "$D/api/opensearch-dashboards/settings" \
  -H 'osd-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"changes":{"defaultIndex":"tickets-logs"}}'

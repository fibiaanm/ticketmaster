#!/bin/sh
# Registra el index template (data como texto NO indexado) y la politica de retencion.
set -e
until curl -sf http://opensearch:9200/_cluster/health >/dev/null; do echo "esperando opensearch..."; sleep 3; done
curl -sf -X PUT http://opensearch:9200/_index_template/tickets-logs \
  -H 'Content-Type: application/json' -d @/init/index-template.json
echo
curl -sf -X PUT http://opensearch:9200/_plugins/_ism/policies/tickets-logs-retention \
  -H 'Content-Type: application/json' -d @/init/ism-policy.json || true
echo

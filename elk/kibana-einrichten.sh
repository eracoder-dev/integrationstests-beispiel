#!/bin/sh
# Legt die Data View für die Anwendungslogs an. Läuft einmal nach dem Start von Kibana.
set -e
KIBANA=http://kibana:5601

echo "Lege Data View an ..."
curl -s -X POST "$KIBANA/api/data_views/data_view" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"data_view":{"id":"antragsbearbeitung-logs","title":"antragsbearbeitung-logs-*","name":"Antragsbearbeitung Logs","timeFieldName":"@timestamp"},"override":true}' \
  -o /dev/null -w "Status %{http_code}\n"

echo "Setze sie als Standard ..."
curl -s -X POST "$KIBANA/api/data_views/default" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"data_view_id":"antragsbearbeitung-logs","force":true}' \
  -o /dev/null -w "Status %{http_code}\n"

echo "Fertig. Kibana: http://localhost:5601/app/discover"

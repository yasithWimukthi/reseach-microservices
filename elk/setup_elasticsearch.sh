#!/bin/bash
# ─────────────────────────────────────────────────────────
#  setup_elasticsearch.sh
#
#  Run this ONCE after docker-compose up to:
#    1. Create the index template (correct field mappings)
#    2. Verify Elasticsearch is receiving logs
#
#  Usage:
#    bash elk/setup_elasticsearch.sh
# ─────────────────────────────────────────────────────────

ES_URL="http://localhost:9200"

echo ""
echo "════════════════════════════════════════════════"
echo "  Setting up Elasticsearch for MSc Research"
echo "════════════════════════════════════════════════"
echo ""

# ── Wait for Elasticsearch ────────────────────────────
echo "Waiting for Elasticsearch to be ready..."
until curl -s "$ES_URL/_cluster/health" | grep -q '"status"'; do
  echo "  Not ready yet — retrying in 5s..."
  sleep 5
done
echo "  ✓ Elasticsearch is up"
echo ""

# ── Create index template ─────────────────────────────
# This ensures numeric fields (latency, heap%, cpu%) are
# stored as numbers — not strings — so ML queries work correctly
echo "Creating index template: microservices-logs"
curl -s -X PUT "$ES_URL/_index_template/microservices-logs-template" \
  -H "Content-Type: application/json" \
  -d '{
    "index_patterns": ["microservices-logs-*", "microservices-alerts-*"],
    "template": {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "refresh_interval": "5s"
      },
      "mappings": {
        "properties": {
          "@timestamp":        { "type": "date" },
          "service":           { "type": "keyword" },
          "level":             { "type": "keyword" },
          "message":           { "type": "text" },
          "logger":            { "type": "keyword" },
          "thread":            { "type": "keyword" },
          "environment":       { "type": "keyword" },
          "event_type":        { "type": "keyword" },
          "injection_type":    { "type": "keyword" },
          "injection_status":  { "type": "keyword" },
          "response_time_ms":  { "type": "float" },
          "duration_ms":       { "type": "float" },
          "heap_used_pct":     { "type": "float" },
          "cpu_load_pct":      { "type": "float" },
          "tags":              { "type": "keyword" }
        }
      }
    }
  }' | python3 -m json.tool
echo ""
echo "  ✓ Index template created"
echo ""

# ── Verify logs are flowing ───────────────────────────
echo "Checking if logs are flowing into Elasticsearch..."
sleep 10
DOC_COUNT=$(curl -s "$ES_URL/microservices-logs-*/_count" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('count', 0))" 2>/dev/null || echo "0")
echo "  Current log document count: $DOC_COUNT"
echo ""

if [ "$DOC_COUNT" -gt "0" ]; then
  echo "  ✓ Logs are flowing — ELK stack is working correctly"
else
  echo "  ⚠ No logs yet — make sure:"
  echo "    1. Microservices are running and generating logs"
  echo "    2. load_generator.py is running to produce traffic"
  echo "    3. Wait 30s and run this script again"
fi

echo ""
echo "════════════════════════════════════════════════"
echo "  Access points:"
echo "    Kibana         → http://localhost:5601"
echo "    Elasticsearch  → http://localhost:9200"
echo "    Logstash API   → http://localhost:9600"
echo "════════════════════════════════════════════════"
echo ""
echo "  Kibana setup (first time only):"
echo "    1. Open http://localhost:5601"
echo "    2. Go to Stack Management → Index Patterns"
echo "    3. Create pattern: microservices-logs-*"
echo "    4. Set time field: @timestamp"
echo "    5. Go to Discover to see your logs"
echo "════════════════════════════════════════════════"
echo ""

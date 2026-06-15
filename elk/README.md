# ELK Stack — Log Centralisation
## MSc Research: Early Failure Prediction in Microservices

---

## Architecture

```
Spring Boot Services
       │
       │  (writes JSON logs to ./logs/)
       ▼
   Filebeat
       │
       │  (ships log lines to port 5044)
       ▼
   Logstash  ──── parses, enriches, tags failure events
       │
       │  (indexes to Elasticsearch)
       ▼
 Elasticsearch  ──── stores all logs with correct field types
       │
       ├──▶  Kibana      (visualise logs — port 5601)
       └──▶  ML Pipeline (Python queries Elasticsearch directly)
```

---

## What Logstash Does to Each Log

Every log line is enriched with:

| Added field        | Example value         | Used for                        |
|--------------------|-----------------------|---------------------------------|
| `@timestamp`       | 2025-01-15T10:23:45Z  | Time-series queries             |
| `level`            | ERROR                 | Error rate features             |
| `service`          | order-service         | Per-service aggregation         |
| `event_type`       | failure_injection     | Labelling training data         |
| `injection_type`   | MEMORY_LEAK           | Failure type classification     |
| `response_time_ms` | 4523.0                | Latency features                |
| `heap_used_pct`    | 78.3                  | Memory features                 |
| `cpu_load_pct`     | 91.2                  | CPU features                    |

---

## Indices in Elasticsearch

| Index pattern              | Contents                          |
|----------------------------|-----------------------------------|
| `microservices-logs-*`     | ALL logs — primary ML data source |
| `microservices-alerts-*`   | Failure injection events only     |

---

## Setup Steps

### Step 1 — Start everything
```bash
# From project root
docker-compose up --build
```

### Step 2 — Create index template (run once)
```bash
bash elk/setup_elasticsearch.sh
```

### Step 3 — Start generating logs
```bash
cd load-generator
python load_generator.py
```

### Step 4 — Open Kibana
```
http://localhost:5601
```
First time setup in Kibana:
1. Stack Management → Index Patterns → Create
2. Pattern: `microservices-logs-*`
3. Time field: `@timestamp`
4. Go to Discover — your logs will appear

---

## Verify Elasticsearch Directly

```bash
# Check cluster health
curl http://localhost:9200/_cluster/health?pretty

# Count all log documents
curl http://localhost:9200/microservices-logs-*/_count?pretty

# Count failure injection events only
curl http://localhost:9200/microservices-alerts-*/_count?pretty

# See latest 5 logs from order-service
curl -X GET "http://localhost:9200/microservices-logs-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 5,
    "sort": [{ "@timestamp": "desc" }],
    "query": { "term": { "service": "order-service" } }
  }'

# Count ERROR logs per service (last 1 hour)
curl -X GET "http://localhost:9200/microservices-logs-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 0,
    "query": { "term": { "level": "ERROR" } },
    "aggs": {
      "by_service": {
        "terms": { "field": "service" }
      }
    }
  }'
```

---

## Memory Requirements

| Component     | RAM     |
|---------------|---------|
| Elasticsearch | ~512MB  |
| Logstash      | ~256MB  |
| Kibana        | ~300MB  |
| Filebeat      | ~50MB   |
| **Total ELK** | ~1.1GB  |

Make sure Docker Desktop has at least **6GB RAM** allocated:
Docker Desktop → Settings → Resources → Memory → set to 6GB+

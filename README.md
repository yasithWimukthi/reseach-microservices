# Microservices Testbed — MSc Research
## Early Failure Prediction in Microservices Using Log Pattern Analysis

---

## Services

| Service           | Port | Description                        |
|-------------------|------|------------------------------------|
| API Gateway       | 8080 | Routes all external requests       |
| Order Service     | 8081 | Manages orders, calls Inventory + Payment |
| Inventory Service | 8082 | Manages product stock              |
| Payment Service   | 8083 | Processes payments (configurable failure rate) |
| PostgreSQL (Order)| 5432 | Order database                     |
| PostgreSQL (Inv)  | 5433 | Inventory database                 |
| PostgreSQL (Pay)  | 5434 | Payment database                   |

---

## Quick Start

### 1. Build and start all services
```bash
docker-compose up --build
```

### 2. Seed inventory data
```bash
curl -X POST http://localhost:8082/api/inventory/add \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","productName":"Laptop","quantity":100}'

curl -X POST http://localhost:8082/api/inventory/add \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-002","productName":"Phone","quantity":200}'
```

### 3. Create a test order
```bash
curl -X POST http://localhost:8080/orders/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-001",
    "quantity": 2,
    "totalAmount": 2500.00
  }'
```

### 4. Check service health
```bash
curl http://localhost:8080/health
curl http://localhost:8081/api/orders/health
curl http://localhost:8082/api/inventory/health
curl http://localhost:8083/api/payments/health
```

---

## Log Files

Logs are written to `./logs/` directory in JSON format:
```
logs/
├── order-service/order-service.log
├── inventory-service/inventory-service.log
├── payment-service/payment-service.log
└── api-gateway/api-gateway.log
```

Each log entry contains:
- `timestamp` — ISO 8601 timestamp
- `level` — INFO / WARN / ERROR / DEBUG
- `service` — service name
- `message` — structured log message
- `logger` — Java class
- MDC fields: `requestId`, `customerId`, `productId`

---

## Failure Injection

### Increase payment failure rate (simulates payment gateway issues)
```bash
curl -X POST http://localhost:8083/api/payments/admin/failure-rate \
  -H "Content-Type: application/json" \
  -d '{"rate": 0.8}'
```

### Reset to normal
```bash
curl -X POST http://localhost:8083/api/payments/admin/failure-rate \
  -H "Content-Type: application/json" \
  -d '{"rate": 0.05}'
```

---

## Next Steps
1. Add Fluentd for log shipping → Elasticsearch
2. Build Python ML pipeline for feature extraction
3. Train XGBoost and LSTM models
4. Build prediction API (FastAPI)
5. Build React dashboard

# Trip Planning Service - Setup & Verification Checklist

Complete this checklist to verify the entire Navio architecture works end-to-end.

## Phase 1: Infrastructure Setup ✅

### 1.1 Start Docker Infrastructure
```bash
cd c:\Users\User\Desktop\Full Stack\Capstone\Navio
docker compose up -d
```

**Verify all services are running:**
```bash
docker compose ps
```

Expected running services:
- ✅ navio-postgres (5432)
- ✅ navio-keycloak (8180)
- ✅ navio-kafka (9092)
- ✅ navio-zookeeper (2181)
- ✅ navio-pgadmin (5050)
- ✅ navio-zipkin (9411)
- ✅ navio-minio (9000)
- ✅ navio-loki-read (3101)
- ✅ navio-loki-write (3102)
- ✅ navio-loki-backend (3103)
- ✅ navio-nginx-loki (3100)
- ✅ navio-prometheus (9090)
- ✅ navio-grafana (3000)
- ✅ navio-alloy (12345)

**Wait 30 seconds for Grafana to start, then verify:**
- Grafana: http://localhost:3000 (auto-login, no password)
- Prometheus: http://localhost:9090
- Zipkin: http://localhost:9411

---

## Phase 2: Spring Cloud Infrastructure Services (Run in order)

Open 4 terminal windows and run these services simultaneously:

### 2.1 Terminal 1: Discovery Server (Eureka)
```bash
cd server/discovery-server
mvn clean install
mvn spring-boot:run
```

**Verify:**
- Logs should show: `Started DiscoveryServerApplication in X seconds`
- Port 8761 should be listening

### 2.2 Terminal 2: Config Server
```bash
cd server/configuration-server
mvn clean install
mvn spring-boot:run
```

**Verify:**
- Logs should show: `Started ConfigurationServerApplication in X seconds`
- Port 8888 should be listening
- Config files should be loaded from `src/main/resources/config/*.yml`

### 2.3 Terminal 3: API Gateway
```bash
cd server/api-gateway
mvn clean install
mvn spring-boot:run
```

**Verify:**
- Logs should show: `Started ApiGatewayApplication in X seconds`
- Should register with Eureka
- Port 8080 should be listening
- Should route requests to downstream services

### 2.4 Terminal 4: Trip Planning Service
```bash
cd server/trip-planning-service
mvn clean install
mvn spring-boot:run
```

**Verify:**
- Logs should show: `Started TripPlanningServiceApplication in X seconds`
- Should connect to PostgreSQL (tripplanner/tripplanner)
- Should connect to Kafka (kafka:9092)
- Should register with Eureka
- Should send traces to Zipkin
- Port 8082 should be listening

---

## Phase 3: Verify Service Discovery & Registration

### 3.1 Eureka Dashboard
Access: http://localhost:8761

Should show:
- ✅ api-gateway (UP)
- ✅ trip-planning-service (UP)
- ✅ user-management-service (future)

### 3.2 Config Server Status
```bash
curl http://localhost:8888/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"}
  }
}
```

---

## Phase 4: Verify Service Connectivity

### 4.1 Test API Gateway Routes
```bash
# Test gateway health
curl http://localhost:8080/actuator/health

# Test trip planning service route
curl http://localhost:8080/api/trips/health
```

### 4.2 Test Direct Service Access
```bash
# Direct to trip planning service
curl http://localhost:8082/actuator/health
```

---

## Phase 5: Verify Observability Stack

### 5.1 Prometheus Metrics
Access: http://localhost:9090

In the query box, search for:
- `jvm_memory_used` — should show metrics from running services
- `spring_boot_application_info` — should list all services

### 5.2 Zipkin Traces
Access: http://localhost:9411

1. Click "Find Traces"
2. Select service from dropdown (api-gateway, trip-planning-service)
3. Should see trace details with latency breakdown

### 5.3 Grafana Dashboard
Access: http://localhost:3000

Add data sources (if not auto-discovered):
- Loki: http://nginx-loki:3100
- Prometheus: http://prometheus:9090
- Zipkin: http://zipkin:9411

Create a simple dashboard:
1. Go to "+" → Dashboard
2. Add Prometheus panel: `jvm_memory_used`
3. Should see metrics updating in real-time

### 5.4 Check Logs in Loki
1. Grafana → Explore
2. Select "Loki" datasource
3. Query: `{container_name="navio-trip-planning"}`
4. Should see logs from trip-planning service

---

## Phase 6: Verify Database & Message Broker

### 6.1 PostgreSQL (pgAdmin)
Access: http://localhost:5050

1. Login: admin@navio.example.com / admin
2. Add server: postgres:5432
3. Database: tripplanner
4. User: tripplanner / tripplanner
5. Tables should be auto-created by Hibernate

### 6.2 Kafka
```bash
# List topics
docker exec navio-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Should see topics created by services
```

---

## Phase 7: Load Test (Optional)

Once all services are running, send test requests:

```bash
# Generate trace data
for i in {1..10}; do
  curl http://localhost:8080/api/trips/health
  sleep 1
done
```

Then check:
1. Prometheus: New metrics appear
2. Zipkin: New traces appear
3. Grafana: Dashboard updates

---

## Troubleshooting

### Service won't start
```bash
# Check Java version (must be 25)
java -version

# Check ports are not in use
netstat -ano | findstr :8080  # Windows
lsof -i :8080                 # Mac/Linux

# Check logs for errors
mvn spring-boot:run -X  # Verbose mode
```

### Can't connect to PostgreSQL
```bash
# Verify container is running
docker compose ps postgres

# Test connection
psql -h localhost -U tripplanner -d tripplanner -c "SELECT 1"
```

### Kafka connection issues
```bash
# Verify Kafka is healthy
docker compose logs kafka | tail -20

# Check Zookeeper connection
docker exec navio-kafka zookeeper-shell localhost:2181 ls /
```

### Zipkin not receiving traces
- Check `management.tracing.sampling.probability: 1.0` in configs
- Verify `spring-boot-micrometer-tracing-brave` dependency exists
- Check logs for trace errors

### Prometheus not scraping metrics
- Ensure `management.endpoints.web.exposure.include: health,metrics,prometheus`
- Verify `/actuator/prometheus` is accessible on each service
- Check Prometheus targets: http://localhost:9090/targets

---

## Success Criteria ✅

All of these should pass:

- [ ] All 4 Spring Cloud services started successfully
- [ ] Eureka shows all services as UP
- [ ] API Gateway routes requests to downstream services
- [ ] PostgreSQL connected and tables created
- [ ] Kafka topics created
- [ ] Prometheus scrapes metrics from all services
- [ ] Zipkin receives traces
- [ ] Grafana displays dashboards
- [ ] Loki aggregates logs
- [ ] No errors in service logs

Once all pass, your infrastructure is **production-ready**! 🚀

---

## Next Steps

1. Implement domain logic in Trip Planning Service
2. Implement User Management Service (same pattern)
3. Add API endpoints and tests
4. Create Grafana dashboards for business metrics
5. Set up CI/CD pipeline for deployment

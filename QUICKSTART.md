# Quick Start Guide - Trip Planning Service

**TL;DR**: Run these 5 commands to get everything working.

---

## Step 1: Start Infrastructure (Wait 30 seconds)

```bash
cd c:\Users\User\Desktop\Full Stack\Capstone\Navio
docker compose up -d
```

✅ Verify:
- http://localhost:3000 (Grafana - auto login, no password)
- http://localhost:9090 (Prometheus)
- http://localhost:9411 (Zipkin)

---

## Step 2: Open 4 Terminals

Run these in **separate terminal windows simultaneously**:

### Terminal 1: Discovery Server
```bash
cd server/discovery-server
mvn clean install
mvn spring-boot:run
```
✅ Wait for: `Started DiscoveryServerApplication`

### Terminal 2: Config Server
```bash
cd server/configuration-server
mvn clean install
mvn spring-boot:run
```
✅ Wait for: `Started ConfigurationServerApplication`

### Terminal 3: API Gateway
```bash
cd server/api-gateway
mvn clean install
mvn spring-boot:run
```
✅ Wait for: `Started ApiGatewayApplication`

### Terminal 4: Trip Planning Service
```bash
cd server/trip-planning-service
mvn clean install
mvn spring-boot:run
```
✅ Wait for: `Started TripPlanningServiceApplication`

---

## Step 3: Verify All Services Running

```bash
# All should return HTTP 200 UP
curl http://localhost:8761/actuator/health   # Eureka
curl http://localhost:8888/actuator/health   # Config Server
curl http://localhost:8080/actuator/health   # API Gateway
curl http://localhost:8082/actuator/health   # Trip Planning Service
```

---

## Step 4: Check Service Discovery

Open: http://localhost:8761

Should show:
- ✅ api-gateway (UP)
- ✅ trip-planning-service (UP)

---

## Step 5: View Observability

### 📊 Metrics & Logs
- Grafana: http://localhost:3000

### 📍 Distributed Traces
- Zipkin: http://localhost:9411
- Select "trip-planning-service" from dropdown
- Click "Find Traces"

### 📈 Raw Metrics
- Prometheus: http://localhost:9090
- Query: `jvm_memory_used`

---

## Test It Works

### Make a test request through API Gateway
```bash
curl http://localhost:8080/actuator/health
```

### See the trace in Zipkin
1. Go to http://localhost:9411
2. Click "Find Traces"
3. Select "api-gateway" service
4. Should show the request you just made

---

## Stop Everything

```bash
# Stop services (Ctrl+C in each terminal)

# Stop Docker
docker compose down
```

---

## Next Steps

See `TODOS.md` for full implementation roadmap:
```bash
cat TODOS.md
```

---

## Troubleshooting

**Port already in use?**
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Mac/Linux
lsof -i :8080
kill -9 <PID>
```

**PostgreSQL not connecting?**
```bash
docker compose logs postgres
```

**Eureka empty?**
- Check Discovery Server logs
- Wait 10 seconds, then refresh browser

**No traces in Zipkin?**
- Verify tracing dependencies in pom.xml
- Check `management.tracing.sampling.probability: 1.0` in config

**Grafana not showing metrics?**
- Wait 30 seconds for first metrics to arrive
- Prometheus scrape interval is 15s
- Refresh Grafana page

---

## Success = All Dashboards Green ✅

- [ ] Eureka: http://localhost:8761 (all UP)
- [ ] Grafana: http://localhost:3000 (metrics flowing)
- [ ] Zipkin: http://localhost:9411 (traces appearing)
- [ ] Prometheus: http://localhost:9090 (targets UP)

🚀 **You're ready to code!**

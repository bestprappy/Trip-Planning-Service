# Trip Planning Service - Implementation TODOs

## ✅ Phase 0: Infrastructure & Configuration (COMPLETE)

- [x] Docker compose with PostgreSQL, Kafka, Zookeeper
- [x] Observability stack (Zipkin, Prometheus, Grafana, Loki, Alloy)
- [x] Spring Cloud Config Server setup
- [x] Service configuration (trip-planning-service.yml)
- [x] Java 25 compatibility verified
- [x] pom.xml with all dependencies

---

## Phase 1: Startup & Verification (DO THIS FIRST)

### 1.1 Infrastructure
- [ ] Start Docker: `docker compose up -d`
- [ ] Wait 30s for all services to start
- [ ] Verify Grafana: http://localhost:3000 (auto-login)
- [ ] Verify Prometheus: http://localhost:9090
- [ ] Verify Zipkin: http://localhost:9411

### 1.2 Spring Cloud Services (Run in 4 terminals, in order)
```
Terminal 1: cd server/discovery-server && mvn spring-boot:run
Terminal 2: cd server/configuration-server && mvn spring-boot:run
Terminal 3: cd server/api-gateway && mvn spring-boot:run
Terminal 4: cd server/trip-planning-service && mvn spring-boot:run
```

- [ ] Discovery Server starts (port 8761)
- [ ] Config Server starts (port 8888)
- [ ] API Gateway starts (port 8080)
- [ ] Trip Planning Service starts (port 8082)

### 1.3 Verify Service Discovery
- [ ] Check Eureka: http://localhost:8761
- [ ] All services show as "UP"
- [ ] Config loaded from Config Server
- [ ] Traces appearing in Zipkin
- [ ] Metrics appearing in Prometheus

---

## Phase 2: Trip Planning Service Domain Layer

### 2.1 Entity & Model
- [ ] Create Trip entity class
  - `id` (UUID)
  - `userId` (String)
  - `destination` (String)
  - `startDate` (LocalDateTime)
  - `endDate` (LocalDateTime)
  - `status` (PLANNED, IN_PROGRESS, COMPLETED, CANCELLED)
  - `createdAt` (LocalDateTime)
  - `updatedAt` (LocalDateTime)

- [ ] Create TripRequest DTO
- [ ] Create TripResponse DTO
- [ ] Create Trip Repository (JpaRepository)

### 2.2 Service Layer
- [ ] Create TripService interface
- [ ] Implement TripServiceImpl
  - `createTrip(TripRequest)`
  - `getTrip(id)`
  - `updateTrip(id, TripRequest)`
  - `deleteTrip(id)`
  - `getTripsByUser(userId)`

### 2.3 Controller Layer
- [ ] Create TripController
  - `POST /api/trips` → Create trip
  - `GET /api/trips/{id}` → Get trip details
  - `GET /api/trips/user/{userId}` → List user's trips
  - `PUT /api/trips/{id}` → Update trip
  - `DELETE /api/trips/{id}` → Delete trip

---

## Phase 3: Event Publishing (Kafka)

### 3.1 Event Definition
- [ ] Create TripCreatedEvent
- [ ] Create TripUpdatedEvent
- [ ] Create TripDeletedEvent

### 3.2 Event Publishing
- [ ] Configure Kafka topic: `trip-events`
- [ ] Publish events from TripService
- [ ] Add correlation IDs for tracing

### 3.3 Event Listening (Future)
- [ ] Listen for user events
- [ ] Listen for media service events

---

## Phase 4: Database & Schema

### 4.1 Flyway Migrations
- [ ] Create `V1__Create_trips_table.sql`
  ```sql
  CREATE TABLE trips (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
  );
  ```

- [ ] Test migration: `mvn flyway:migrate`
- [ ] Verify in pgAdmin: http://localhost:5050

---

## Phase 5: Testing

### 5.1 Unit Tests
- [ ] Test TripService business logic
- [ ] Test input validation
- [ ] Test database operations

### 5.2 Integration Tests
- [ ] Test API endpoints
- [ ] Test database integration
- [ ] Test Kafka event publishing

### 5.3 Run All Tests
```bash
cd server/trip-planning-service
mvn test
```

- [ ] All tests pass
- [ ] Code coverage > 80%

---

## Phase 6: Observability

### 6.1 Tracing
- [ ] Verify traces in Zipkin: http://localhost:9411
- [ ] Check trace latency breakdown
- [ ] Verify distributed tracing across services

### 6.2 Metrics
- [ ] Verify metrics in Prometheus: http://localhost:9090
- [ ] Query `jvm_memory_used{service="trip-planning-service"}`
- [ ] Check request latencies

### 6.3 Logs
- [ ] Verify logs in Loki via Grafana
- [ ] Query: `{service="trip-planning-service"}`
- [ ] Check for errors or warnings

### 6.4 Grafana Dashboard
- [ ] Create dashboard: "Trip Planning Service"
- [ ] Add panels:
  - Request rate (QPS)
  - Error rate
  - Latency (P50, P95, P99)
  - Database connections
  - Kafka lag

---

## Phase 7: API Documentation

### 7.1 Swagger/OpenAPI
- [ ] Add SpringFox/Springdoc dependency
- [ ] Configure OpenAPI documentation
- [ ] Access Swagger UI: http://localhost:8082/swagger-ui.html

### 7.2 Update API Documentation
- [ ] Update `docs/api/Navio Open API.yaml`
- [ ] Document all endpoints
- [ ] Document request/response schemas

---

## Phase 8: Security & IAM (Phase 2)

- [ ] Add Keycloak integration (commented in pom.xml)
- [ ] Implement OAuth2 resource server config
- [ ] Add @PreAuthorize annotations
- [ ] Test with Keycloak tokens

---

## Phase 9: Performance & Optimization

- [ ] Add caching for frequently accessed trips
- [ ] Optimize database queries (indexes, n+1 problems)
- [ ] Add query pagination
- [ ] Load test with k6 or JMeter
- [ ] Monitor performance metrics

---

## Phase 10: Production Readiness

- [ ] Error handling (global exception handler)
- [ ] Retry logic for Kafka
- [ ] Circuit breaker for external services
- [ ] Rate limiting on API endpoints
- [ ] Input validation & sanitization
- [ ] Audit logging
- [ ] Deployment documentation

---

## Quick Reference: Commands

### Run Infrastructure
```bash
docker compose up -d                    # Start all
docker compose logs -f                  # View logs
docker compose down -v                  # Stop & clean
```

### Run Services
```bash
cd server/discovery-server && mvn spring-boot:run
cd server/configuration-server && mvn spring-boot:run
cd server/api-gateway && mvn spring-boot:run
cd server/trip-planning-service && mvn spring-boot:run
```

### Test Service
```bash
cd server/trip-planning-service
mvn test                                # Run unit tests
mvn verify                              # Run all tests
mvn clean install                       # Build & install
```

### Monitor
```bash
# Eureka (service discovery)
http://localhost:8761

# Grafana (dashboards)
http://localhost:3000

# Prometheus (metrics)
http://localhost:9090

# Zipkin (traces)
http://localhost:9411

# pgAdmin (database)
http://localhost:5050
```

### Test API
```bash
# Get all trips for user
curl http://localhost:8080/api/trips/user/{userId}

# Create trip
curl -X POST http://localhost:8080/api/trips \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Paris",
    "startDate": "2026-09-01T00:00:00",
    "endDate": "2026-09-10T00:00:00"
  }'

# Get trip details
curl http://localhost:8080/api/trips/{tripId}
```

---

## Success Criteria

Once all phases complete, you should have:

✅ Trip Planning Service running on 8082  
✅ Registering with Eureka (8761)  
✅ Loading config from Config Server (8888)  
✅ Routable via API Gateway (8080)  
✅ Connected to PostgreSQL  
✅ Publishing events to Kafka  
✅ Exporting traces to Zipkin  
✅ Exporting metrics to Prometheus  
✅ Aggregating logs in Loki  
✅ Full observability in Grafana  
✅ Comprehensive test coverage  
✅ Production-ready code  

🚀 **Ready for deployment!**

---

## Notes

- Each phase builds on the previous one
- Don't skip infrastructure setup (Phase 1)
- Testing should happen throughout, not just Phase 5
- Observability is mandatory, not optional
- Keep the SETUP.md checklist handy for reference

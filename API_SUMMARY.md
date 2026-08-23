# Trip Planning Service - API Summary

Complete REST API for managing user trips.

---

## Architecture Overview

```
┌──────────────┐
│  API Gateway │ (8080)
│              │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│ Trip Planning Service (8082) │
│                              │
│  ┌─────────────────────────┐ │
│  │ TripController          │ │
│  │ - Create               │ │
│  │ - Read (one/many)      │ │
│  │ - Update               │ │
│  │ - Delete               │ │
│  │ - Change Status        │ │
│  └──────────┬──────────────┘ │
│             │                │
│  ┌──────────▼──────────────┐ │
│  │ TripService            │ │
│  │ (Business Logic)       │ │
│  └──────────┬──────────────┘ │
│             │                │
│  ┌──────────▼──────────────┐ │
│  │ TripRepository         │ │
│  │ (Database Access)      │ │
│  └──────────┬──────────────┘ │
│             │                │
└─────────────┼────────────────┘
              │
    ┌─────────▼────────┐
    │   PostgreSQL     │
    │   (tripplanner)  │
    └──────────────────┘
```

---

## REST API Endpoints

### 1. Create Trip
```
POST /api/trips
Content-Type: application/json

Request:
{
  "userId": "user-123",
  "destination": "Paris",
  "startDate": "2026-09-01T09:00:00",
  "endDate": "2026-09-10T18:00:00"
}

Response: 201 Created
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-123",
  "destination": "Paris",
  "startDate": "2026-09-01T09:00:00",
  "endDate": "2026-09-10T18:00:00",
  "status": "PLANNED",
  "createdAt": "2026-08-22T10:30:00",
  "updatedAt": "2026-08-22T10:30:00"
}
```

---

### 2. Get Trip Details
```
GET /api/trips/{id}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-123",
  "destination": "Paris",
  ...
}

Errors:
- 404 Not Found: Trip doesn't exist
```

---

### 3. List User's Trips
```
GET /api/trips/user/{userId}

Response: 200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "destination": "Paris",
    ...
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "userId": "user-123",
    "destination": "Tokyo",
    ...
  }
]
```

---

### 4. Get Trips by Status
```
GET /api/trips/user/{userId}/status/{status}

Status values: PLANNED, IN_PROGRESS, COMPLETED, CANCELLED

Response: 200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "status": "PLANNED",
    ...
  }
]
```

---

### 5. Update Trip
```
PUT /api/trips/{id}
Content-Type: application/json

Request:
{
  "userId": "user-123",
  "destination": "Rome",
  "startDate": "2026-09-15T09:00:00",
  "endDate": "2026-09-22T18:00:00"
}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "destination": "Rome",
  "updatedAt": "2026-08-22T11:00:00",
  ...
}

Errors:
- 400 Bad Request: Invalid input
- 404 Not Found: Trip doesn't exist
```

---

### 6. Update Trip Status
```
PATCH /api/trips/{id}/status/{status}

Status values: PLANNED, IN_PROGRESS, COMPLETED, CANCELLED

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "updatedAt": "2026-08-22T11:15:00",
  ...
}
```

---

### 7. Delete Trip
```
DELETE /api/trips/{id}

Response: 204 No Content

Errors:
- 404 Not Found: Trip doesn't exist
```

---

## Data Model

### Trip Entity
```
{
  "id": "UUID",                    // Auto-generated
  "userId": "String",              // Required
  "destination": "String",         // Required
  "startDate": "LocalDateTime",    // Required
  "endDate": "LocalDateTime",      // Required
  "status": "TripStatus",          // PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
  "createdAt": "LocalDateTime",    // Auto-set on creation
  "updatedAt": "LocalDateTime"     // Auto-updated on changes
}
```

### Database Schema
```sql
CREATE TABLE trips (
  id VARCHAR(36) PRIMARY KEY,
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

---

## Error Responses

### 400 Bad Request
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "User ID is required"
}
```

### 404 Not Found
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Trip not found: invalid-id"
}
```

### 500 Internal Server Error
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred"
}
```

---

## Class Structure

```
src/main/java/com/navio/tripplanningservice/
├── controller/
│   └── TripController.java        # REST endpoints
├── service/
│   ├── TripService.java           # Service interface
│   └── TripServiceImpl.java        # Business logic
├── repository/
│   └── TripRepository.java        # Data access
├── domain/
│   └── Trip.java                  # Entity model
├── dto/
│   ├── TripRequest.java           # Create/update request
│   └── TripResponse.java          # Response format
└── TripPlanningServiceApplication.java

src/test/java/com/navio/tripplanningservice/
├── VerificationTests.java         # Spring context tests
└── controller/
    └── TripControllerTest.java    # API endpoint tests
```

---

## Testing

### Run All Tests
```bash
cd server/trip-planning-service
mvn test
```

### Run Specific Test
```bash
mvn test -Dtest=TripControllerTest
```

### Test Coverage
```bash
mvn jacoco:report
# Report: target/site/jacoco/index.html
```

### Manual Testing
See `REST_API_TESTS.md` for cURL examples

---

## Observability

### Tracing
- Traces automatically sent to Zipkin (9411)
- Each request gets a unique trace ID
- Latency tracked per operation

### Metrics
- Prometheus scrapes `/actuator/prometheus` (9090)
- JVM metrics (memory, GC, threads)
- HTTP metrics (requests, latency, errors)
- Database metrics (connections, query time)

### Logs
- Structured logging to Grafana Loki (3100)
- Log levels: INFO (default), DEBUG (development)
- Logs queryable by service, class, method

---

## TODO: Future Enhancements

- [ ] **Pagination**: Limit/offset for list endpoints
- [ ] **Filtering**: Date range, status, destination search
- [ ] **Sorting**: By date, status, creation time
- [ ] **Events**: Publish Kafka events (TripCreated, TripUpdated, TripDeleted)
- [ ] **Security**: Keycloak OAuth2/JWT authentication
- [ ] **Validation**: Date overlap, past date checks
- [ ] **Documentation**: Swagger/OpenAPI
- [ ] **Rate Limiting**: Prevent abuse
- [ ] **Caching**: Redis for frequently accessed trips
- [ ] **Notifications**: Send events to other services

---

## Quick Links

- **API Gateway**: http://localhost:8080
- **Trip Service**: http://localhost:8082
- **Zipkin Traces**: http://localhost:9411
- **Prometheus Metrics**: http://localhost:9090
- **Grafana Dashboard**: http://localhost:3000
- **PostgreSQL (pgAdmin)**: http://localhost:5050

---

## Deployment Checklist

Before production deployment:

- [ ] All tests passing
- [ ] Error handling verified
- [ ] Database migrations applied
- [ ] Environment variables configured
- [ ] Security checks passed
- [ ] Performance tested (load test)
- [ ] Monitoring configured
- [ ] Alerting configured
- [ ] Backup strategy in place
- [ ] Rollback procedure documented

---

## Support

For issues or questions:
1. Check logs: `docker compose logs trip-planning-service`
2. Check traces: Zipkin (http://localhost:9411)
3. Check metrics: Prometheus (http://localhost:9090)
4. Check database: pgAdmin (http://localhost:5050)

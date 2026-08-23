# Trip Planning Service - REST API Tests

Test all endpoints with these cURL commands.

## Prerequisites

Services must be running:
```bash
# Terminal 1
cd server/discovery-server && mvn spring-boot:run

# Terminal 2
cd server/configuration-server && mvn spring-boot:run

# Terminal 3
cd server/api-gateway && mvn spring-boot:run

# Terminal 4
cd server/trip-planning-service && mvn spring-boot:run
```

Verify service is running:
```bash
curl http://localhost:8082/actuator/health
```

---

## API Endpoints

### 1. Create Trip
**POST /api/trips**

```bash
curl -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Paris",
    "startDate": "2026-09-01T09:00:00",
    "endDate": "2026-09-10T18:00:00"
  }'
```

**Expected Response:**
```json
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

Save the `id` for next tests:
```bash
TRIP_ID="550e8400-e29b-41d4-a716-446655440000"
```

---

### 2. Get Trip Details
**GET /api/trips/{id}**

```bash
curl http://localhost:8082/api/trips/$TRIP_ID
```

**Expected Response:** Same as create response

---

### 3. List User's Trips
**GET /api/trips/user/{userId}**

```bash
curl http://localhost:8082/api/trips/user/user-123
```

**Expected Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "destination": "Paris",
    ...
  }
]
```

---

### 4. Get Trips by Status
**GET /api/trips/user/{userId}/status/{status}**

```bash
curl http://localhost:8082/api/trips/user/user-123/status/PLANNED
```

**Status values:** PLANNED, IN_PROGRESS, COMPLETED, CANCELLED

---

### 5. Update Trip
**PUT /api/trips/{id}**

```bash
curl -X PUT http://localhost:8082/api/trips/$TRIP_ID \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Rome",
    "startDate": "2026-09-15T09:00:00",
    "endDate": "2026-09-20T18:00:00"
  }'
```

**Expected Response:** Updated trip details

---

### 6. Update Trip Status
**PATCH /api/trips/{id}/status/{status}**

```bash
curl -X PATCH http://localhost:8082/api/trips/$TRIP_ID/status/IN_PROGRESS
```

**Expected Response:** Trip with updated status

```bash
# Mark as completed
curl -X PATCH http://localhost:8082/api/trips/$TRIP_ID/status/COMPLETED
```

---

### 7. Delete Trip
**DELETE /api/trips/{id}**

```bash
curl -X DELETE http://localhost:8082/api/trips/$TRIP_ID
```

**Expected Response:** HTTP 204 No Content

---

## Complete Test Sequence

Run these in order to test the full flow:

```bash
# 1. Create first trip
TRIP_1=$(curl -s -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Paris",
    "startDate": "2026-09-01T09:00:00",
    "endDate": "2026-09-10T18:00:00"
  }' | jq -r '.id')

echo "Created trip: $TRIP_1"

# 2. Create second trip
TRIP_2=$(curl -s -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Tokyo",
    "startDate": "2026-10-01T09:00:00",
    "endDate": "2026-10-15T18:00:00"
  }' | jq -r '.id')

echo "Created trip: $TRIP_2"

# 3. List all trips for user
echo "User trips:"
curl -s http://localhost:8082/api/trips/user/user-123 | jq

# 4. Update first trip
echo "Updating trip $TRIP_1..."
curl -s -X PUT http://localhost:8082/api/trips/$TRIP_1 \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Rome",
    "startDate": "2026-09-15T09:00:00",
    "endDate": "2026-09-22T18:00:00"
  }' | jq

# 5. Change status
echo "Marking trip $TRIP_1 as IN_PROGRESS..."
curl -s -X PATCH http://localhost:8082/api/trips/$TRIP_1/status/IN_PROGRESS | jq

# 6. Get by status
echo "Trips in progress:"
curl -s http://localhost:8082/api/trips/user/user-123/status/IN_PROGRESS | jq

# 7. Delete
echo "Deleting trip $TRIP_1..."
curl -s -X DELETE http://localhost:8082/api/trips/$TRIP_1
echo "Deleted"

# 8. Verify deletion
echo "Remaining trips:"
curl -s http://localhost:8082/api/trips/user/user-123 | jq
```

---

## Test via API Gateway

Test routing through API Gateway (port 8080):

```bash
# Create trip via gateway
curl -X POST http://localhost:8080/api/trips/createTrip \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-456",
    "destination": "London",
    "startDate": "2026-11-01T09:00:00",
    "endDate": "2026-11-07T18:00:00"
  }'
```

> **Note:** Adjust gateway routes in `api-gateway.yml` if needed.

---

## Verify Observability

### 1. Check Traces in Zipkin
```
http://localhost:9411
- Select "trip-planning-service" from dropdown
- Click "Find Traces"
- Should see requests you just made
```

### 2. Check Metrics in Prometheus
```
http://localhost:9090
Query: jvm_memory_used{job="trip-planning-service"}
```

### 3. Check Logs in Grafana
```
http://localhost:3000
- Explore → Loki
- Query: {service="trip-planning-service"}
```

---

## Common Errors

### 400 Bad Request
Missing required fields or invalid format
```bash
# ✗ Missing userId
curl -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -d '{"destination": "Paris"}'

# ✓ Valid request
curl -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "destination": "Paris",
    "startDate": "2026-09-01T09:00:00",
    "endDate": "2026-09-10T18:00:00"
  }'
```

### 404 Not Found
Trip ID doesn't exist
```bash
curl http://localhost:8082/api/trips/invalid-id
# Response: 404 Not Found
```

### 500 Internal Server Error
Check service logs:
```bash
# In the terminal running trip-planning-service
# Look for error stack traces
```

---

## TODO: Enhancements

- [ ] Add pagination to list endpoints
- [ ] Add date range filtering
- [ ] Add sorting options
- [ ] Add search by destination
- [ ] Add authentication (Keycloak)
- [ ] Add validation for date overlap
- [ ] Add API documentation (Swagger)
- [ ] Add rate limiting
- [ ] Add caching

---

## Success Indicators

✅ All endpoints return 200 OK (or appropriate status)
✅ Data persists in PostgreSQL (verify via pgAdmin)
✅ Traces appear in Zipkin within 1 second
✅ Metrics appear in Prometheus within 15 seconds
✅ Logs appear in Grafana Loki
✅ No errors in service logs

When all pass → **API is working correctly!** 🎉

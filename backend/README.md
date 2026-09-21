# Emergency Ambulance Tracking System — Backend

Spring Boot 3 / Java 17 backend for a real-time ambulance dispatch platform.

## What's actually implemented (not just buzzwords)

| Feature | How |
|---|---|
| Live GPS tracking | STOMP over WebSocket (`/ws`), driver pushes to `/app/location.update`, broker fans out to `/topic/ambulance.{id}.location` |
| Nearest-ambulance dispatch | Haversine formula computed in a MySQL native query (`AmbulanceRepository.findNearestAvailable`), sorted server-side, not filtered in Java memory |
| No double-booking under concurrent requests | `@Version` optimistic locking on `Ambulance`; `DispatchService` catches `ObjectOptimisticLockingFailureException` and retries against the next-nearest candidate |
| RBAC | Spring Security + JWT, three roles: `PATIENT`, `DRIVER`, `DISPATCHER`, enforced per-endpoint in `SecurityConfig` |
| Stateless auth | JWT bearer tokens (`jjwt`), 24h expiry by default |

## Tests

```bash
mvn test
```

Covers the parts of this project that actually matter to explain in an
interview: `DispatchServiceTest` (nearest-match dispatch, the optimistic-lock
retry-to-next-candidate behavior under simulated contention, the
NO_AMBULANCE_AVAILABLE path, and priority-based radius widening),
`BookingServiceTest` (cancel/complete correctly freeing the ambulance and
notifying the driver), and `AuthServiceTest` (registration validation rules).
All are Mockito-based unit tests - no database or Spring context needed to
run them.

## Docker

```bash
docker build -t ambulance-tracking-backend .
docker run -p 8080:8080 --env-file .env ambulance-tracking-backend
```

See `../DEPLOYMENT.md` at the project root for deploying this for real
(Railway + Vercel) rather than just running it locally in a container.

## Run it

1. Create a MySQL 8 database (or let `createDatabaseIfNotExist=true` do it) and update credentials in
   `src/main/resources/application.properties`.
2. `mvn spring-boot:run` (or import as a Maven project into IntelliJ/Eclipse and run `AmbulanceTrackingApplication`).
3. API base: `http://localhost:8080`, WebSocket endpoint: `ws://localhost:8080/ws` (SockJS).

## Key endpoints

```
POST /api/auth/register        { fullName, email, password, phone, role, vehicleNumber? }
POST /api/auth/login           { email, password } -> { token, userId, fullName, role }

POST /api/bookings             (PATIENT) create + auto-dispatch nearest ambulance
GET  /api/bookings/my          (PATIENT) my booking history
PUT  /api/bookings/{id}/cancel (PATIENT)
GET  /api/bookings/pending     (DISPATCHER)
PUT  /api/bookings/{id}/complete

GET  /api/ambulances           (DRIVER/DISPATCHER)
PUT  /api/ambulances/{id}/location   { lat, lng }  -- REST fallback; WebSocket is primary path
PUT  /api/ambulances/{id}/status     { status }
```

STOMP:
```
CONNECT /ws (SockJS)
SEND    /app/location.update            { ambulanceId, lat, lng }
SUB     /topic/ambulance.{id}.location
SUB     /topic/dispatch.{bookingId}
```

## Why these specific technical choices (interview talking points)

- **WebSockets over polling**: a patient screen polling every 2s costs a full HTTP
  round-trip + auth check per poll and caps freshness at the poll interval. One
  persistent STOMP connection lets the driver push the instant position changes and
  the broker fans it out to every subscriber at once.
- **Haversine in SQL, not Java**: computing distance for every ambulance in the
  application layer means pulling the whole table into memory on every dispatch.
  Doing it in the `WHERE`/`ORDER BY` of a native query lets MySQL do the filtering
  and sorting, and only nearest-first candidates ever reach the JVM.
- **Optimistic locking (`@Version`) over pessimistic locking**: ambulance dispatch
  is rare-conflict, high-read. Pessimistic row locks would serialize every dispatch
  attempt against the same ambulance row even when there's no real contention.
  Optimistic locking costs nothing on the common path and only pays a retry when
  two requests genuinely collide on the same vehicle.

## Suggested next steps for the resume/demo

- Deploy backend on Render/Railway, frontend on Vercel/Netlify, link both from GitHub README.
- Add Swagger/OpenAPI (`springdoc-openapi-starter-webmvc-ui`) for live API docs.
- Add a `GET /api/ambulances/{id}` and a small driver-side location-simulator script for the demo GIF.

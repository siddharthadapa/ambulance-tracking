# 🚑 Emergency Ambulance Tracking System

Full-stack real-time ambulance dispatch platform — patients request an ambulance, get matched to the nearest available driver automatically, and watch it move toward them live on a map.

**Stack:** Java 17, Spring Boot 3, Spring Security, Spring Data JPA, MySQL, STOMP over WebSocket (backend) · React 18, Leaflet, @stomp/stompjs (frontend)

## 📁 Project Structure
ambulance-tracking/
├── backend/     # Spring Boot API + WebSocket broker + dispatch logic
└── frontend/    # React app (Patient / Driver / Dispatcher dashboards)

## 👥 Roles & Flows

- **Patient** — requests an ambulance from their current location, sees live status (PENDING → DISPATCHED → COMPLETED), and watches the assigned ambulance move toward them on the map in real time.
- **Driver** — goes online (marks their ambulance AVAILABLE) and streams GPS position over WebSocket every few seconds. Falls back to a simulated jitter movement if the browser has no geolocation (useful for demos without a physical device).
- **Dispatcher** — live fleet map showing every ambulance's position and status, plus a list of pending booking requests.

## 🚀 What's Actually Implemented

| Feature | How |
|---|---|
| Live GPS tracking | STOMP over WebSocket (/ws) — driver pushes to /app/location.update, broker fans out to /topic/ambulance.{id}.location |
| Nearest-ambulance dispatch | Haversine distance computed in a MySQL native query (AmbulanceRepository.findNearestAvailable), sorted server-side — not filtered in Java memory |
| No double-booking under concurrent requests | @Version optimistic locking on Ambulance; DispatchService catches ObjectOptimisticLockingFailureException and retries against the next-nearest candidate |
| Role-based access control | Spring Security + JWT, three roles: PATIENT, DRIVER, DISPATCHER, enforced per-endpoint in SecurityConfig |
| Stateless auth | JWT bearer tokens (jjwt), 24h expiry by default |

## 🏗️ How the Real-Time Pieces Fit Together

- src/websocket/socket.js (frontend) wraps @stomp/stompjs + sockjs-client into a singleton STOMP client shared across the app.
- Drivers publish to /app/location.update; the backend re-broadcasts to /topic/ambulance.{id}.location, which patients and the dispatcher both subscribe to for the same ambulance — one GPS frame updates every screen watching that vehicle.
- Dispatch outcomes broadcast on /topic/dispatch.{bookingId} — this is how a patient's screen flips from "Requesting..." to showing the assigned driver's name and phone number the instant the backend's optimistic-lock dispatch loop succeeds.

## 📡 Key Endpoints

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

STOMP:
CONNECT /ws (SockJS)
SEND    /app/location.update            { ambulanceId, lat, lng }
SUB     /topic/ambulance.{id}.location
SUB     /topic/dispatch.{bookingId}

## 🧠 Why These Specific Technical Choices

- **WebSockets over polling** — a patient screen polling every 2s costs a full HTTP round-trip + auth check per poll and caps freshness at the poll interval. One persistent STOMP connection lets the driver push instant position changes and the broker fans it out to every subscriber at once.
- **Haversine in SQL, not Java** — computing distance for every ambulance in the application layer means pulling the whole table into memory on every dispatch. Doing it in the WHERE/ORDER BY of a native query lets MySQL do the filtering and sorting, so only nearest-first candidates ever reach the JVM.
- **Optimistic locking (@Version) over pessimistic locking** — ambulance dispatch is rare-conflict, high-read. Pessimistic row locks would serialize every dispatch attempt against the same ambulance row even when there's no real contention. Optimistic locking costs nothing on the common path and only pays a retry when two requests genuinely collide on the same vehicle.

## ⚙️ Run It Locally

**Backend**
1. Create a MySQL 8 database (or let createDatabaseIfNotExist=true handle it) and update credentials in backend/src/main/resources/application.properties.
2. cd backend && mvn spring-boot:run
3. API: http://localhost:8080 · WebSocket: ws://localhost:8080/ws (SockJS)

**Frontend**
cd frontend
npm install
cp .env.example .env   # adjust API/WS URLs if backend isn't on localhost:8080
npm run dev

Runs on http://localhost:5173 (Vite).

**Try it out:** Register one account as DRIVER (with a vehicle number), one as PATIENT, and optionally one as DISPATCHER. Log the driver in and go online; log the patient in and request an ambulance — the ambulance marker should start moving on the patient's map within a few seconds.

## 🧪 Tests

cd backend && mvn test

Covers DispatchServiceTest (nearest-match dispatch, the optimistic-lock retry-to-next-candidate behavior under simulated contention, the NO_AMBULANCE_AVAILABLE path, and priority-based radius widening), BookingServiceTest (cancel/complete correctly freeing the ambulance and notifying the driver), and AuthServiceTest (registration validation rules). All are Mockito-based unit tests — no database or Spring context needed to run them.

## 👤 Author
Adapa Phani Venkata Siddhardha — [LinkedIn](https://linkedin.com/in/adapa-phani-venkata-siddhardha) | [GitHub](https://github.com/siddharthadapa)

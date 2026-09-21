# Emergency Ambulance Tracking System

Full-stack real-time ambulance dispatch platform — Java 17 / Spring Boot backend,
React 18 / Leaflet frontend, MySQL, STOMP WebSockets.

## Folders

- `backend/` — Spring Boot API, WebSocket broker, dispatch logic. See `backend/README.md`.
- `frontend/` — React app (Patient / Driver / Dispatcher dashboards). See `frontend/README.md`.

## Quick start

1. **Database**: have MySQL 8 running locally (or point `application.properties` at a remote one).
2. **Backend**: `cd backend && mvn spring-boot:run` → `http://localhost:8080`
3. **Frontend**: `cd frontend && npm install && npm start` → `http://localhost:3000`
4. Register one account as `DRIVER` (with a vehicle number), one as `PATIENT`, and optionally one
   as `DISPATCHER`. Log the driver in and go online; log the patient in and request an ambulance.

## What makes this resume-worthy (vs. a generic CRUD app)

- **Real-time GPS tracking** over STOMP/WebSocket instead of REST polling.
- **Nearest-ambulance dispatch** computed with a Haversine SQL query, not filtered in Java memory.
- **No double-booking under concurrent requests** — `@Version` optimistic locking on the
  `Ambulance` table, with automatic retry against the next-nearest candidate on a conflict.
- **Role-based access control** (`PATIENT` / `DRIVER` / `DISPATCHER`) enforced via Spring Security + JWT.

See `backend/README.md` for the endpoint list and the reasoning behind each architectural choice —
useful as interview talking points.

## Suggested next steps before you ship this on your resume

- Deploy: frontend → Vercel/Netlify, backend → Render/Railway (set `app.cors.allowed-origins` and
  the frontend's `.env` to match your deployed URLs).
- Add Swagger/OpenAPI docs (`springdoc-openapi-starter-webmvc-ui`).
- Record a short GIF of the driver-moves / patient-sees-it-live flow for your GitHub README.
- Push driver location updates from a real device (or the simulator built into the driver
  dashboard) so the demo link on your resume is actually interactive.

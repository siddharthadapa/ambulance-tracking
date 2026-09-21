# Emergency Ambulance Tracking System — Frontend

React 18 frontend with live Leaflet map tracking and STOMP/WebSocket real-time updates.

## Setup

```bash
cd frontend
npm install
cp .env.example .env   # adjust API/WS URLs if backend isn't on localhost:8080
npm run dev
```

Runs on `http://localhost:5173` (Vite). `npm run build` produces a production
build in `dist/`; `npm run preview` serves that build locally to sanity-check it.

## Roles / flows

- **Patient** (`/patient`) — requests an ambulance from current geolocation, sees live status
  (`PENDING` → `DISPATCHED` → `COMPLETED`), and watches the assigned ambulance move on the map
  in real time once dispatched.
- **Driver** (`/driver`) — goes online (marks their ambulance `AVAILABLE`) and streams GPS
  position over WebSocket every few seconds. Falls back to a simulated jitter movement if the
  browser has no geolocation (useful for demos without a physical device).
- **Dispatcher** (`/dispatcher`) — live fleet map showing every ambulance's current position and
  status, plus a list of pending (undispatched) booking requests.

## How the real-time pieces fit together

- `src/websocket/socket.js` wraps `@stomp/stompjs` + `sockjs-client` into a singleton STOMP
  client shared across the app.
- Drivers `publish` to `/app/location.update`; the backend re-broadcasts to
  `/topic/ambulance.{id}.location`, which patients and the dispatcher both subscribe to for the
  same ambulance — so a single GPS frame updates every screen watching that vehicle.
- Dispatch outcomes broadcast on `/topic/dispatch.{bookingId}`, which is how a patient's screen
  flips from "Requesting..." to showing the assigned driver's name and phone number the instant
  the backend's optimistic-lock dispatch loop succeeds.

## Notes for the demo / README GIF

- Open two browser windows: one logged in as a driver (go online), one as a patient (request an
  ambulance). The patient's map marker for the ambulance should start moving within a few
  seconds of the driver going online.
- Open a third window as a dispatcher to show the live fleet board updating in parallel — this is
  the shot worth capturing for the GitHub README GIF mentioned in the resume-polish notes.

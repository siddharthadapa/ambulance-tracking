import React, { useEffect, useRef, useState } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import MapView from '../components/MapView';
import { publishLocation, getStompClient, subscribeWhenReady } from '../websocket/socket';

const DEFAULT_CENTER = { lat: 16.5062, lng: 80.6480 };
const TICK_MS = 2000;
const IDLE_WANDER_STEP_DEG = 0.0004;   // small patrol wander while waiting for a job
const TRAVEL_STEP_FRACTION = 0.22;     // move this fraction of remaining distance toward target each tick
const ARRIVAL_THRESHOLD_DEG = 0.0006;  // ~60-70m - close enough to call it "arrived"
const HOSPITAL_OFFSET = { lat: 0.028, lng: 0.021 }; // demo-only fixed offset for "nearby hospital"

// Phases: 'idle' -> 'to_patient' -> 'at_patient' -> 'to_hospital' -> 'at_hospital' -> back to 'idle'
export default function DriverDashboard() {
  const { user, logout } = useAuth();
  const [ambulance, setAmbulance] = useState(null);
  const [online, setOnline] = useState(false);
  const [pos, setPos] = useState(DEFAULT_CENTER);
  const [phase, setPhase] = useState('idle');
  const [job, setJob] = useState(null); // { bookingId, pickupLat, pickupLng, pickupAddress }

  const posRef = useRef(pos);
  const phaseRef = useRef(phase);
  const jobRef = useRef(job);
  const intervalRef = useRef(null);
  const pauseTicksRef = useRef(0);

  useEffect(() => { posRef.current = pos; }, [pos]);
  useEffect(() => { phaseRef.current = phase; }, [phase]);
  useEffect(() => { jobRef.current = job; }, [job]);

  useEffect(() => {
    api.get('/ambulances').then(({ data }) => {
      const mine = data.find((a) => a.driver && a.driver.id === user.id);
      if (mine) {
        setAmbulance(mine);
        const start = { lat: mine.currentLat || DEFAULT_CENTER.lat, lng: mine.currentLng || DEFAULT_CENTER.lng };
        setPos(start);
        posRef.current = start;
      }
    });
    getStompClient();
  }, [user.id]);

  // Listen for the backend telling us we've been assigned a pickup (or that
  // our current assignment was cancelled). See DispatchService/BookingService
  // on the backend - they push to /topic/ambulance.{id}.assignment.
  useEffect(() => {
    if (!ambulance) return undefined;
    const sub = subscribeWhenReady(`/topic/ambulance.${ambulance.id}.assignment`, (msg) => {
      if (msg.cleared) {
        setJob(null);
        setPhase('idle');
      } else {
        setJob(msg);
        setPhase('to_patient');
      }
    });
    return () => sub.unsubscribe && sub.unsubscribe();
  }, [ambulance]);

  const goOnline = async () => {
    if (!ambulance) return;
    await api.put(`/ambulances/${ambulance.id}/status`, { status: 'AVAILABLE' });
    setOnline(true);
    startLoop();
  };

  const goOffline = async () => {
    if (!ambulance) return;
    await api.put(`/ambulances/${ambulance.id}/status`, { status: 'OFFLINE' });
    setOnline(false);
    stopLoop();
    setJob(null);
    setPhase('idle');
  };

  const broadcast = (lat, lng) => {
    setPos({ lat, lng });
    posRef.current = { lat, lng };
    publishLocation(ambulance.id, lat, lng);
  };

  const stepToward = (target) => {
    const current = posRef.current;
    const dLat = target.lat - current.lat;
    const dLng = target.lng - current.lng;
    const distance = Math.sqrt(dLat * dLat + dLng * dLng);

    if (distance <= ARRIVAL_THRESHOLD_DEG) {
      broadcast(target.lat, target.lng); // snap exactly onto the target on arrival
      return true; // arrived
    }

    broadcast(current.lat + dLat * TRAVEL_STEP_FRACTION, current.lng + dLng * TRAVEL_STEP_FRACTION);
    return false;
  };

  const tick = () => {
    const currentPhase = phaseRef.current;
    const currentJob = jobRef.current;

    if (currentPhase === 'idle') {
      // Gentle patrol wander so the vehicle doesn't look frozen while waiting,
      // without actually going anywhere in particular.
      const current = posRef.current;
      broadcast(
        current.lat + (Math.random() - 0.5) * IDLE_WANDER_STEP_DEG,
        current.lng + (Math.random() - 0.5) * IDLE_WANDER_STEP_DEG
      );
      return;
    }

    if (currentPhase === 'to_patient' && currentJob) {
      const arrived = stepToward({ lat: currentJob.pickupLat, lng: currentJob.pickupLng });
      if (arrived) {
        pauseTicksRef.current = 2; // simulate loading the patient for ~4s
        setPhase('at_patient');
      }
      return;
    }

    if (currentPhase === 'at_patient') {
      pauseTicksRef.current -= 1;
      if (pauseTicksRef.current <= 0) {
        setPhase('to_hospital');
      }
      return;
    }

    if (currentPhase === 'to_hospital' && currentJob) {
      const hospital = {
        lat: currentJob.pickupLat + HOSPITAL_OFFSET.lat,
        lng: currentJob.pickupLng + HOSPITAL_OFFSET.lng,
      };
      const arrived = stepToward(hospital);
      if (arrived) {
        setPhase('at_hospital');
        api.put(`/bookings/${currentJob.bookingId}/complete`).catch(() => {});
        setJob(null);
        setPhase('idle'); // ambulance is free again immediately; backend also flips it AVAILABLE
      }
      return;
    }
  };

  const startLoop = () => {
    stopLoop();
    intervalRef.current = setInterval(tick, TICK_MS);
  };

  const stopLoop = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  useEffect(() => () => stopLoop(), []);

  const phaseLabel = {
    idle: 'Idle - waiting for a request',
    to_patient: 'Heading to patient',
    at_patient: 'Arrived - loading patient',
    to_hospital: 'Transporting to hospital',
    at_hospital: 'Arrived at hospital',
  }[phase];

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h2>🚑 Driver Dashboard</h2>
        <div>
          <span>{user?.fullName}</span>
          <button className="link-btn" onClick={logout}>Logout</button>
        </div>
      </header>

      <div className="dashboard-grid">
        <div className="panel">
          {ambulance ? (
            <>
              <h3>Vehicle {ambulance.vehicleNumber}</h3>
              <p>Status: <strong>{online ? 'AVAILABLE (broadcasting location)' : 'OFFLINE'}</strong></p>
              {!online ? (
                <button className="danger" onClick={goOnline}>Go online</button>
              ) : (
                <button className="link-btn" onClick={goOffline}>Go offline</button>
              )}
              {online && (
                <div className="driver-card">
                  <p>{phaseLabel}</p>
                  {job && <p className="hint">Pickup: {job.pickupAddress || `${job.pickupLat.toFixed(4)}, ${job.pickupLng.toFixed(4)}`}</p>}
                </div>
              )}
              <p className="hint">Lat: {pos.lat.toFixed(5)}, Lng: {pos.lng.toFixed(5)}</p>
            </>
          ) : (
            <p>No ambulance linked to this account yet.</p>
          )}
        </div>
        <div className="panel map-panel">
          <MapView
            center={[pos.lat, pos.lng]}
            markers={[{ id: 'me', lat: pos.lat, lng: pos.lng, label: ambulance?.vehicleNumber || 'You' }]}
            followMarkerId="me"
          />
        </div>
      </div>
    </div>
  );
}

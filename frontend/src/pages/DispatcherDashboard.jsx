import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import MapView from '../components/MapView';
import { subscribeWhenReady } from '../websocket/socket';

const DEFAULT_CENTER = [16.5062, 80.6480];
const PRIORITY_ORDER = { NORMAL: 0, SERIOUS: 1, CRITICAL: 2, ICU: 3 };

export default function DispatcherDashboard() {
  const { user, logout } = useAuth();
  const [ambulances, setAmbulances] = useState([]);
  const [pending, setPending] = useState([]);

  const load = () => {
    api.get('/ambulances').then((r) => setAmbulances(r.data));
    api.get('/bookings/pending').then((r) => setPending(r.data));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 8000); // periodic refresh for pending list / statuses
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const subs = ambulances.map((a) =>
      subscribeWhenReady(`/topic/ambulance.${a.id}.location`, (loc) => {
        setAmbulances((prev) => prev.map((x) => (x.id === loc.ambulanceId ? { ...x, currentLat: loc.lat, currentLng: loc.lng } : x)));
      })
    );
    return () => subs.forEach((s) => s.unsubscribe && s.unsubscribe());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ambulances.length]);

  const markers = ambulances
    .filter((a) => a.currentLat && a.currentLng)
    .map((a) => ({ id: a.id, lat: a.currentLat, lng: a.currentLng, label: `${a.vehicleNumber} - ${a.status}` }));

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h2>🚑 Dispatcher Console</h2>
        <div>
          <span>{user?.fullName}</span>
          <button className="link-btn" onClick={logout}>Logout</button>
        </div>
      </header>

      <div className="dashboard-grid">
        <div className="panel">
          <h3>Fleet ({ambulances.length})</h3>
          <table className="fleet-table">
            <thead>
              <tr><th>Vehicle</th><th>Status</th><th>Driver</th></tr>
            </thead>
            <tbody>
              {ambulances.map((a) => (
                <tr key={a.id}>
                  <td>{a.vehicleNumber}</td>
                  <td><span className={`badge badge-${a.status}`}>{a.status}</span></td>
                  <td>{a.driver?.fullName || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h3>Pending requests ({pending.length})</h3>
          {pending.length === 0 && <p className="hint">No pending requests.</p>}
          {[...pending].sort((a, b) => PRIORITY_ORDER[b.priority] - PRIORITY_ORDER[a.priority]).map((b) => (
            <div key={b.id} className="driver-card">
              <p>
                Booking #{b.id} - {b.status}
                {' '}
                <span className={`priority-badge priority-${b.priority}`}>{b.priority}</span>
              </p>
              <p>{b.pickupAddress || `${b.pickupLat.toFixed(4)}, ${b.pickupLng.toFixed(4)}`}</p>
            </div>
          ))}
        </div>

        <div className="panel map-panel">
          <MapView center={DEFAULT_CENTER} markers={markers} height="600px" />
        </div>
      </div>
    </div>
  );
}

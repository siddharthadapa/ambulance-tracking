import React, { useEffect, useState } from 'react';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import MapView from '../components/MapView';
import { subscribeWhenReady } from '../websocket/socket';

const DEFAULT_CENTER = [16.5062, 80.6480]; // Vijayawada, AP

const PRIORITY_OPTIONS = [
  { value: 'NORMAL', label: 'Normal', hint: 'Non-urgent transport' },
  { value: 'SERIOUS', label: 'Serious', hint: 'Needs prompt attention' },
  { value: 'CRITICAL', label: 'Critical', hint: 'Life-threatening' },
  { value: 'ICU', label: 'ICU', hint: 'Requires ICU-equipped ambulance' },
];

export default function PatientDashboard() {
  const { user, logout } = useAuth();
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [priority, setPriority] = useState('NORMAL');
  const [coords, setCoords] = useState(null);
  const [activeBooking, setActiveBooking] = useState(null);
  const [ambulanceLoc, setAmbulanceLoc] = useState(null);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => setCoords({ lat: DEFAULT_CENTER[0], lng: DEFAULT_CENTER[1] })
      );
    } else {
      setCoords({ lat: DEFAULT_CENTER[0], lng: DEFAULT_CENTER[1] });
    }
  }, []);

  useEffect(() => {
    if (!activeBooking) return;
    const dispatchSub = subscribeWhenReady(`/topic/dispatch.${activeBooking.id}`, (msg) => {
      setStatus(msg.status);
      if (msg.status === 'DISPATCHED' && msg.ambulanceId) {
        const locSub = subscribeWhenReady(`/topic/ambulance.${msg.ambulanceId}.location`, (loc) => {
          setAmbulanceLoc({ lat: loc.lat, lng: loc.lng });
        });
        return () => locSub.unsubscribe();
      }
    });
    return () => dispatchSub.unsubscribe && dispatchSub.unsubscribe();
  }, [activeBooking]);

  const requestAmbulance = async (e) => {
    e.preventDefault();
    if (!coords) return;
    setLoading(true);
    try {
      const { data } = await api.post('/bookings', {
        pickupLat: coords.lat,
        pickupLng: coords.lng,
        pickupAddress: address,
        notes,
        priority,
      });
      setActiveBooking(data);
      setStatus(data.status);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not request ambulance');
    } finally {
      setLoading(false);
    }
  };

  const markers = [];
  if (coords) markers.push({ id: 'me', lat: coords.lat, lng: coords.lng, label: 'Your location' });
  if (ambulanceLoc) markers.push({ id: 'amb', lat: ambulanceLoc.lat, lng: ambulanceLoc.lng, label: activeBooking?.ambulanceVehicleNumber || 'Ambulance' });

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h2>🚑 Patient Dashboard</h2>
        <div>
          <span>{user?.fullName}</span>
          <button className="link-btn" onClick={logout}>Logout</button>
        </div>
      </header>

      <div className="dashboard-grid">
        <div className="panel">
          {!activeBooking ? (
            <form onSubmit={requestAmbulance}>
              <h3>Request an ambulance</h3>

              <label>Priority</label>
              <div className="priority-group">
                {PRIORITY_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={`priority-btn priority-${opt.value}${priority === opt.value ? ' selected' : ''}`}
                    onClick={() => setPriority(opt.value)}
                    title={opt.hint}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
              <p className="hint">{PRIORITY_OPTIONS.find((o) => o.value === priority)?.hint}</p>

              <label>Pickup address (optional label)</label>
              <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="e.g. Near City Hospital gate" />
              <label>Notes for the driver</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} />
              <p className="hint">{coords ? `Using your current location (${coords.lat.toFixed(4)}, ${coords.lng.toFixed(4)})` : 'Getting your location...'}</p>
              <button type="submit" className="danger" disabled={loading || !coords}>
                {loading ? 'Requesting...' : 'Request Ambulance'}
              </button>
            </form>
          ) : (
            <div>
              <h3>Booking #{activeBooking.id}</h3>
              <p>
                Status: <strong>{status}</strong>
                {' '}
                <span className={`priority-badge priority-${activeBooking.priority}`}>{activeBooking.priority}</span>
              </p>
              {status === 'DISPATCHED' && (
                <div className="driver-card">
                  <p>🚑 {activeBooking.ambulanceVehicleNumber}</p>
                  <p>Driver: {activeBooking.driverName}</p>
                  <p>Phone: {activeBooking.driverPhone}</p>
                </div>
              )}
              {status === 'NO_AMBULANCE_AVAILABLE' && (
                <p className="error-banner">No ambulance available nearby right now. Please call emergency services directly.</p>
              )}
              <button className="link-btn" onClick={() => { setActiveBooking(null); setStatus(''); setAmbulanceLoc(null); }}>
                New request
              </button>
            </div>
          )}
        </div>

        <div className="panel map-panel">
          {coords && (
            <MapView
              center={[coords.lat, coords.lng]}
              markers={markers}
              followMarkerId={ambulanceLoc ? 'amb' : null}
            />
          )}
        </div>
      </div>
    </div>
  );
}

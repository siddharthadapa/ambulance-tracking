import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';

// Default Leaflet marker icons don't resolve correctly under bundlers;
// point them at the CDN copies explicitly.
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const ambulanceIcon = new L.Icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

// react-leaflet's MapContainer only applies the `center` prop on its
// initial mount - it does NOT recenter the map when `center` changes on
// later renders. Without this, a moving ambulance marker can silently
// drift outside the visible viewport as it updates. This component uses
// the map instance directly (via useMap) to pan the view to follow a
// given [lat, lng] whenever it changes.
function FollowMarker({ position }) {
  const map = useMap();
  useEffect(() => {
    if (position) {
      map.panTo(position, { animate: true, duration: 0.8 });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [position && position[0], position && position[1]]);
  return null;
}

export default function MapView({ center, markers = [], height = '420px', followMarkerId = null }) {
  const followTarget = followMarkerId ? markers.find((m) => m.id === followMarkerId) : null;

  return (
    <div style={{ height, width: '100%', borderRadius: 12, overflow: 'hidden' }}>
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; OpenStreetMap contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {markers.map((m) => (
          <Marker key={m.id} position={[m.lat, m.lng]} icon={ambulanceIcon}>
            <Popup>{m.label}</Popup>
          </Marker>
        ))}
        {followTarget && <FollowMarker position={[followTarget.lat, followTarget.lng]} />}
      </MapContainer>
    </div>
  );
}

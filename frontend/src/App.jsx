import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';
import Login from './pages/Login';
import Register from './pages/Register';
import PatientDashboard from './pages/PatientDashboard';
import DriverDashboard from './pages/DriverDashboard';
import DispatcherDashboard from './pages/DispatcherDashboard';

function Home() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'PATIENT') return <Navigate to="/patient" replace />;
  if (user.role === 'DRIVER') return <Navigate to="/driver" replace />;
  if (user.role === 'DISPATCHER') return <Navigate to="/dispatcher" replace />;
  return <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/patient" element={<PrivateRoute role="PATIENT"><PatientDashboard /></PrivateRoute>} />
          <Route path="/driver" element={<PrivateRoute role="DRIVER"><DriverDashboard /></PrivateRoute>} />
          <Route path="/dispatcher" element={<PrivateRoute role="DISPATCHER"><DispatcherDashboard /></PrivateRoute>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

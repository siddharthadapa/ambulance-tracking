import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await login(email, password);
      routeByRole(data.role, navigate);
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>🚑 Ambulance Tracking</h1>
        <p className="subtitle">Sign in to continue</p>
        {error && <div className="error-banner">{error}</div>}
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</button>
        <p className="switch-link">No account? <Link to="/register">Register</Link></p>
      </form>
    </div>
  );
}

export function routeByRole(role, navigate) {
  if (role === 'PATIENT') navigate('/patient');
  else if (role === 'DRIVER') navigate('/driver');
  else if (role === 'DISPATCHER') navigate('/dispatcher');
  else navigate('/');
}

import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { routeByRole } from './Login';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', phone: '', role: 'PATIENT', vehicleNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await register(form);
      routeByRole(data.role, navigate);
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>🚑 Create account</h1>
        {error && <div className="error-banner">{error}</div>}

        <label>Full name</label>
        <input value={form.fullName} onChange={update('fullName')} required />

        <label>Email</label>
        <input type="email" value={form.email} onChange={update('email')} required />

        <label>Password</label>
        <input type="password" value={form.password} onChange={update('password')} minLength={6} required />

        <label>Phone</label>
        <input value={form.phone} onChange={update('phone')} required />

        <label>I am a</label>
        <select value={form.role} onChange={update('role')}>
          <option value="PATIENT">Patient</option>
          <option value="DRIVER">Ambulance Driver</option>
          <option value="DISPATCHER">Dispatcher / Hospital Admin</option>
        </select>

        {form.role === 'DRIVER' && (
          <>
            <label>Vehicle number</label>
            <input value={form.vehicleNumber} onChange={update('vehicleNumber')} placeholder="e.g. AP16-AM-2201" required />
          </>
        )}

        <button type="submit" disabled={loading}>{loading ? 'Creating...' : 'Create account'}</button>
        <p className="switch-link">Already registered? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}

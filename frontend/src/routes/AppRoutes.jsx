import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import MainLayout from '../components/layout/MainLayout';
import Login from '../pages/Login';
import Home from '../pages/Home';
import Chat from '../pages/Chat';
import NotFound from '../pages/NotFound';

function PrivateRoute({ children }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

export default function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <Login />}
      />
      <Route
        element={
          <PrivateRoute>
            <MainLayout />
          </PrivateRoute>
        }
      >
        <Route path="/dashboard" element={<Home />} />
        <Route path="/chat" element={<Chat />} />
        {/* All previous pages are now sections of Home */}
        <Route path="/documents" element={<Navigate to="/dashboard" replace />} />
        <Route path="/users" element={<Navigate to="/dashboard" replace />} />
        <Route path="/departments" element={<Navigate to="/dashboard" replace />} />
        <Route path="/activity-logs" element={<Navigate to="/dashboard" replace />} />
        <Route path="/admin" element={<Navigate to="/dashboard" replace />} />
        <Route path="/admin/overview" element={<Navigate to="/dashboard" replace />} />
        <Route path="/profile" element={<Navigate to="/dashboard" replace />} />
      </Route>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

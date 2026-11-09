import { Navigate, Outlet, useLocation } from 'react-router-dom';
import Spinner from '@/shared/ui/Spinner';
import { useSession } from '@/features/auth/hooks';

export default function RequireAuth() {
  const { status } = useSession();
  const location = useLocation();
  if (status === 'unknown') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner label="Checking your session" />
      </div>
    );
  }
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

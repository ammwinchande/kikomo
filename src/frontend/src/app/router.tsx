import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router-dom';
import AppShell from './layout/AppShell';
import RequireAuth from './RequireAuth';
import LoginPage from '@/features/auth/LoginPage';
import ApprovalsPage from '@/features/approvals/ApprovalsPage';
import ActivityPage from '@/features/activity/ActivityPage';
import AuditPage from '@/features/audit/AuditPage';
import PolicyPage from '@/features/policy/PolicyPage';
import DemoPage from '@/features/demo/DemoPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <Navigate to="/approvals" replace /> },
          { path: '/approvals', element: <ApprovalsPage /> },
          { path: '/activity', element: <ActivityPage /> },
          { path: '/audit', element: <AuditPage /> },
          { path: '/policy', element: <PolicyPage /> },
          { path: '/demo', element: <DemoPage /> },
          { path: '*', element: <Navigate to="/approvals" replace /> },
        ],
      },
    ],
  },
];

const future = { v7_startTransition: true, v7_relativeSplatPath: true, v7_fetcherPersist: true, v7_normalizeFormMethod: true, v7_partialHydration: true, v7_skipActionErrorRevalidation: true };

export const createAppRouter = () => createBrowserRouter(routes, { future });
export const createTestRouter = (initialEntries: string[]) => createMemoryRouter(routes, { initialEntries, future });

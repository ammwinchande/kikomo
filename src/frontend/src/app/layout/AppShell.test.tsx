import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, screen, userEvent, waitFor } from '@/test/render';
import { state } from '@/test/msw/handlers';
import { useAuthStore } from '@/features/auth/store';
import AppShell from './AppShell';

function renderShell(route = '/approvals') {
  useAuthStore.setState({ status: 'authenticated' });
  return renderWithProviders(
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/approvals" element={<div>Inbox body</div>} />
        <Route path="/activity" element={<div>Activity body</div>} />
      </Route>
    </Routes>,
    { route },
  );
}

describe('AppShell', () => {
  it('shows the section tabs with the pending badge and the sandbox pill', async () => {
    renderShell();
    expect(await screen.findByLabelText('2 pending')).toHaveTextContent('2');
    for (const tab of ['Approvals', 'Activity', 'Audit', 'Policy', 'Demo']) expect(screen.getByRole('link', { name: new RegExp(tab) })).toBeInTheDocument();
    expect(screen.getByText('PayPal sandbox · no real money')).toBeInTheDocument();
    expect(await screen.findByText('MOCK PAYPAL — no real money moves')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Approvals/ })).toHaveAttribute('aria-current', 'page');
  });

  it('navigates between sections', async () => {
    renderShell();
    await userEvent.setup().click(await screen.findByRole('link', { name: 'Activity' }));
    expect(await screen.findByText('Activity body')).toBeInTheDocument();
  });

  it('signs out', async () => {
    renderShell();
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(useAuthStore.getState().status).toBe('anonymous'));
    expect(state.received.some((r) => r.url === '/api/auth/logout')).toBe(true);
  });
});

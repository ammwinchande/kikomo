import { Route } from 'react-router-dom';
import { renderWithProviders, screen, userEvent, waitFor } from '@/test/render';
import { state } from '@/test/msw/handlers';
import { useAuthStore } from './store';
import LoginPage from './LoginPage';

function renderLogin() {
  return renderWithProviders(<LoginPage/>, {
    route: '/login',
    path: '/login',
    extraRoutes: <Route path="/approvals" element={<div>Approvals route</div>}/>,
  });
}

describe('LoginPage', () => {
  it('signs in and navigates to the inbox', async () => {
    state.loggedIn = false;
    renderLogin();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Admin password'), 'admin');
    await user.click(screen.getByRole('button', {name: 'Sign in'}));
    expect(await screen.findByText('Approvals route')).toBeInTheDocument();
    expect(useAuthStore.getState().status).toBe('authenticated');
    expect(state.received.find((r) => r.url === '/api/auth/login')?.body).toEqual({password: 'admin'});
  });

  it('shows an error for a wrong password', async () => {
    state.loggedIn = false;
    renderLogin();
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Admin password'), 'nope');
    await user.click(screen.getByRole('button', {name: 'Sign in'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('That password is not right. Try again.');
    expect(useAuthStore.getState().status).toBe('anonymous');
  });

  it('redirects straight to the inbox when a session already exists', async () => {
    state.loggedIn = true;
    renderLogin();
    await waitFor(() => expect(screen.getByText('Approvals route')).toBeInTheDocument());
  });
});

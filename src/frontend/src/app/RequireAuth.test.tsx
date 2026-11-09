import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, screen } from '@/test/render';
import { state } from '@/test/msw/handlers';
import RequireAuth from './RequireAuth';

describe('RequireAuth', () => {
  it('renders the protected page when the session probe succeeds', async () => {
    renderWithProviders(
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/secret" element={<div>Secret page</div>} />
        </Route>
      </Routes>,
      { route: '/secret' },
    );
    expect(await screen.findByText('Secret page')).toBeInTheDocument();
  });

  it('redirects to login when the session probe is rejected', async () => {
    state.loggedIn = false;
    renderWithProviders(
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/secret" element={<div>Secret page</div>} />
        </Route>
      </Routes>,
      { route: '/secret' },
    );
    expect(await screen.findByText('Login route')).toBeInTheDocument();
  });
});

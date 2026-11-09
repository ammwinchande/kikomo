import { renderWithProviders, screen, userEvent, waitFor, within } from '@/test/render';
import { state } from '@/test/msw/handlers';
import DemoPage from './DemoPage';

describe('DemoPage', () => {
  it('runs the small scenario and streams the steps until done', async () => {
    renderWithProviders(<DemoPage/>);
    expect(await screen.findByText('Demo mode on')).toBeInTheDocument();
    expect(screen.getByText('Nothing has run yet')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', {name: /Run small/}));
    const feed = await screen.findByRole('list', {name: 'Demo steps'});
    await waitFor(() => expect(within(feed).getAllByRole('listitem').filter((li) => li.textContent?.includes('OK_WITHIN_POLICY') || li.textContent?.includes('executed') || li.textContent?.includes('send_payment'))).toHaveLength(3), {timeout: 5000});
    expect(within(feed).getByText('Executed on PayPal sandbox')).toBeInTheDocument();
    expect(state.received.find((r) => r.url === '/api/demo/runs')?.body).toEqual({scenario: 'small'});
  });

  it('offers to approve a waiting large payment right from the feed', async () => {
    renderWithProviders(<DemoPage/>);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: /Run large/}));
    const approve = await screen.findByRole('button', {name: 'Approve it now'});
    await user.click(approve);
    await waitFor(() => expect(state.received.some((r) => r.url === '/api/approvals/a1/approve')).toBe(true));
  });

  it('clears the feed', async () => {
    renderWithProviders(<DemoPage/>);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: /Run large/}));
    await screen.findByText('Waiting for a human');
    await user.click(screen.getByRole('button', {name: 'Clear'}));
    expect(screen.getByText('Nothing has run yet')).toBeInTheDocument();
  });

  it('explains when demo mode is off', async () => {
    state.demoMode = false;
    renderWithProviders(<DemoPage/>);
    expect(await screen.findByText('Demo mode off')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('DEMO_MODE=false');
    expect(screen.getByRole('button', {name: /Run all/})).toBeDisabled();
  });

  it('shows connection details without any token', async () => {
    renderWithProviders(<DemoPage/>);
    expect((await screen.findAllByText(/http:\/\/localhost:8080\/mcp/)).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', {name: /Open \/demo\/malicious-page/})).toHaveAttribute('href', 'http://localhost:8080/demo/malicious-page');
    expect(document.body.textContent).not.toContain('seed-token');
  });
});

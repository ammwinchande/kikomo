import { renderWithProviders, screen, userEvent, within } from '@/test/render';
import ActivityPage from './ActivityPage';

describe('ActivityPage', () => {
  it('lists the calls newest first and explains the first one', async () => {
    renderWithProviders(<ActivityPage />);
    const table = await screen.findByRole('table', { name: 'Agent calls, newest first' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(6);
    expect(rows[0]).toHaveTextContent('Denied');
    expect(rows[3]).toHaveTextContent('kikomo_fetch_url');
    expect(rows[3]).toHaveTextContent('/demo/malicious-page');
    expect(rows[3]).toHaveTextContent('No money');
    expect(rows[0]).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('AGENT_SUSPENDED')).toBeInTheDocument();
  });

  it('updates the Why panel when a row is selected', async () => {
    renderWithProviders(<ActivityPage />);
    const user = userEvent.setup();
    const table = await screen.findByRole('table', { name: 'Agent calls, newest first' });
    await user.click(within(table).getByText('pay-me@evil-payments.example'));
    expect(await screen.findByText('UNTRUSTED_COUNTERPARTY')).toBeInTheDocument();
    expect(screen.getByText(/A page cannot add a payee/)).toBeInTheDocument();
    expect(screen.getByText('Source of payee').nextSibling).toHaveTextContent('kikomo_fetch_url');
    expect(await screen.findByText('Payee on allowlist')).toBeInTheDocument();
    expect(screen.getByText(/"payee_email": "pay-me@evil-payments.example"/)).toBeInTheDocument();
  });

  it('reveals the plain-words explanation on request', async () => {
    renderWithProviders(<ActivityPage />);
    const user = userEvent.setup();
    const table = await screen.findByRole('table', { name: 'Agent calls, newest first' });
    await user.click(within(table).getByText('events@example.com'));
    await user.click(screen.getByRole('button', { name: 'Explain in plain words' }));
    expect(screen.getByRole('note')).toHaveTextContent('Amount is at or above approval threshold.');
    expect(screen.getByText('Approval threshold').nextSibling).toHaveTextContent('$25.00');
  });
});

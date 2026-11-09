import { renderWithProviders, screen, userEvent, within } from '@/test/render';
import { state } from '@/test/msw/handlers';
import { auditEntries } from '@/test/fixtures';
import AuditPage from './AuditPage';

describe('AuditPage', () => {
  it('lists entries oldest first with linked fingerprints', async () => {
    renderWithProviders(<AuditPage/>);
    const first = within(await screen.findByTestId('audit-1'));
    expect(first.getByText('Policy activated')).toBeInTheDocument();
    expect(first.getByText('start')).toBeInTheDocument();
    expect(first.getByText('Not checked yet')).toBeInTheDocument();
    const third = within(screen.getByTestId('audit-3'));
    expect(third.getByText('Payment executed')).toBeInTheDocument();
    expect(third.getByText(auditEntries[1].hash.slice(0, 8))).toBeInTheDocument();
    expect(third.getByText(auditEntries[2].hash.slice(0, 8))).toBeInTheDocument();
  });

  it('verifies the chain against the server', async () => {
    renderWithProviders(<AuditPage/>);
    await screen.findByTestId('audit-1');
    await userEvent.setup().click(screen.getByRole('button', {name: 'Verify chain'}));
    expect(await screen.findByText('Chain intact. 6 entries checked.')).toBeInTheDocument();
    expect(screen.getAllByText('Linked')).toHaveLength(6);
    expect(state.received.some((r) => r.url === '/api/audit/verify')).toBe(true);
  });

  it('reports what the server says when the real chain is broken', async () => {
    state.auditValid = false;
    renderWithProviders(<AuditPage/>);
    await screen.findByTestId('audit-1');
    await userEvent.setup().click(screen.getByRole('button', {name: 'Verify chain'}));
    expect(await screen.findByText('Tampering detected. The server could not verify the chain.')).toBeInTheDocument();
  });

  it('simulates tampering locally, detects it on verify, and repairs', async () => {
    renderWithProviders(<AuditPage/>);
    await screen.findByTestId('audit-1');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', {name: 'Simulate tampering'}));
    expect(screen.getByText(/Simulation only/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Verify chain'}));
    expect(await screen.findByText('Tampering detected at entry 4. Entries 4 to 6 can no longer be trusted.')).toBeInTheDocument();
    expect(within(screen.getByTestId('audit-3')).getByText('Linked')).toBeInTheDocument();
    expect(within(screen.getByTestId('audit-4')).getByText('Fingerprint mismatch')).toBeInTheDocument();
    expect(within(screen.getByTestId('audit-5')).getByText('Untrusted')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Repair'}));
    expect(screen.getByText(/Press Verify chain/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Verify chain'}));
    expect(await screen.findByText('Chain intact. 6 entries checked.')).toBeInTheDocument();
  });
});

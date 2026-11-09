import { renderWithProviders, screen, userEvent, waitFor, within } from '@/test/render';
import { state } from '@/test/msw/handlers';
import { useUiStore } from '@/shared/store/ui-store';
import ApprovalsPage from './ApprovalsPage';

describe('ApprovalsPage', () => {
  it('renders the stat tiles, approval cards, agent card and policy summary', async () => {
    renderWithProviders(<ApprovalsPage/>);
    const card = within(await screen.findByTestId('approval-a1'));
    expect(card.getByText('$480.00')).toBeInTheDocument();
    expect(card.getByText('events@example.com')).toBeInTheDocument();
    expect(card.getByText('Over the approval threshold.')).toBeInTheDocument();
    expect(await card.findByText(/is above the \$25\.00 level/)).toBeInTheDocument();
    expect(card.getByText(/Expires in/)).toBeInTheDocument();

    const second = within(screen.getByTestId('approval-a2'));
    expect(second.getByText('New payee.')).toBeInTheDocument();

    expect(screen.getByText('Waiting for you').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Allowed today').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Blocked today').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Intact')).toBeInTheDocument();

    expect(await screen.findByText('demo-agent')).toBeInTheDocument();
    expect(screen.getByText('$12.00')).toBeInTheDocument();
    expect(screen.getByText('/ $600.00')).toBeInTheDocument();
    expect(await screen.findByText('Version 1')).toBeInTheDocument();
    expect(screen.getByText('Per-payment cap').nextSibling).toHaveTextContent('$500.00');
  });

  it('approves a request and shows the decided state', async () => {
    renderWithProviders(<ApprovalsPage/>);
    const user = userEvent.setup();
    const card = within(await screen.findByTestId('approval-a1'));
    await user.click(card.getByRole('button', {name: 'Approve and send'}));
    expect(await screen.findByText('Approved and executed')).toBeInTheDocument();
    expect(screen.getByTestId('approval-a1')).toHaveTextContent('Approved and executed');
    expect(state.received.find((r) => r.url === '/api/approvals/a1/approve')?.method).toBe('POST');
    await waitFor(() => expect(screen.getByText('Waiting for you').nextSibling).toHaveTextContent('1'));
    expect(useUiStore.getState().toasts.at(-1)?.text).toBe('Approved. The payment was sent to PayPal.');
  });

  it('denies a request', async () => {
    renderWithProviders(<ApprovalsPage/>);
    const card = within(await screen.findByTestId('approval-a2'));
    await userEvent.setup().click(card.getByRole('button', {name: 'Deny'}));
    expect(await screen.findByText('Denied by you. The agent was told no.')).toBeInTheDocument();
    expect(screen.getByTestId('approval-a2')).toHaveTextContent('Denied by you');
    expect(state.received.some((r) => r.url === '/api/approvals/a2/deny')).toBe(true);
  });

  it('explains a conflict when the request was already decided', async () => {
    state.approvals[0].status = 'pending';
    renderWithProviders(<ApprovalsPage/>);
    const card = within(await screen.findByTestId('approval-a1'));
    state.approvals[0].status = 'approved'; // somebody else decided meanwhile
    await userEvent.setup().click(card.getByRole('button', {name: 'Approve and send'}));
    await waitFor(() => expect(useUiStore.getState().toasts.at(-1)?.text).toBe('This request was already decided or has expired.'));
    // The local decision is rolled back, so the card does not claim the payment was executed.
    expect(screen.queryByText('Approved and executed')).not.toBeInTheDocument();
  });

  it('shows the empty state when nothing is pending', async () => {
    state.approvals = [];
    renderWithProviders(<ApprovalsPage/>);
    expect(await screen.findByText('Nothing is waiting on you')).toBeInTheDocument();
  });

  it('suspends and resumes the agent with the kill switch', async () => {
    renderWithProviders(<ApprovalsPage/>);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: 'Suspend agent'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('demo-agent is suspended.');
    expect(state.received.some((r) => r.url === `/api/agents/${state.agents[0].id}/suspend`)).toBe(true);
    await user.click(await screen.findByRole('button', {name: 'Resume agent'}));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });
});

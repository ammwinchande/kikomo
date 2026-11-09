import { act, renderWithProviders, screen, userEvent, within } from '@/test/render';
import { state } from '@/test/msw/handlers';
import { useUiStore } from '@/shared/store/ui-store';
import PolicyPage from './PolicyPage';

describe('PolicyPage', () => {
  it('shows the active policy and compiles a draft with a diff', async () => {
    renderWithProviders(<PolicyPage/>);
    expect(await screen.findByText('Version 1')).toBeInTheDocument();
    expect(screen.getByText('Payees on the allowlist: devtools@example.com, events@example.com')).toBeInTheDocument();
    expect(screen.getByText(/Compile a draft to see exactly what would change/)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', {name: 'Compile draft'}));
    const diff = await screen.findByRole('table', {name: 'Draft policy compared with the active policy'});
    expect(screen.getByText('1 change')).toBeInTheDocument();
    const row = within(diff).getByText('Categories').closest('tr')!;
    expect(row).toHaveTextContent('developer_tools, events');
    expect(row).toHaveTextContent('Changed');
    expect(screen.getByText(/Removes the events category/)).toBeInTheDocument();
    const compile = state.received.find((r) => r.url === '/api/policies/compile');
    expect(compile?.body).toEqual({
      text: 'Allow dev tools up to $500, approve over $25',
      allowlist: ['devtools@example.com', 'events@example.com']
    });
  });

  it('activates the draft and announces the new version', async () => {
    renderWithProviders(<PolicyPage/>);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: 'Compile draft'}));
    await user.click(await screen.findByRole('button', {name: 'Confirm and activate'}));
    expect(await screen.findByText(/New policy is live\. Version \d+ now decides every call\./)).toBeInTheDocument();
    const activate = state.received.find((r) => r.url.endsWith('/activate'));
    expect(activate?.method).toBe('POST');
    expect((activate?.body as { allowedCategories: string[] }).allowedCategories).toEqual(['developer_tools']);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('discards a draft', async () => {
    renderWithProviders(<PolicyPage/>);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', {name: 'Compile draft'}));
    await user.click(await screen.findByRole('button', {name: 'Discard'}));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText(/Compile a draft to see exactly what would change/)).toBeInTheDocument();
  });

  it('shows the server validation errors when activation is rejected', async () => {
    renderWithProviders(<PolicyPage/>);
    const user = userEvent.setup();
    await user.clear(await screen.findByLabelText('In plain words'));
    await user.type(screen.getByLabelText('In plain words'), 'Allow anything up to $5000');
    await user.click(screen.getByRole('button', {name: 'Compile draft'}));
    await screen.findByRole('button', {name: 'Confirm and activate'});
    // Make the draft exceed the hard cap before confirming.
    const {usePolicyStore} = await import('./store');
    act(() => usePolicyStore.setState((s) => ({draft: {...s.draft!, perTxCapMinor: 500000}})));
    await user.click(screen.getByRole('button', {name: 'Confirm and activate'}));
    await screen.findByText(/Confirm and activate/);
    await new Promise((r) => setTimeout(r, 50));
    expect(useUiStore.getState().toasts.at(-1)?.text).toContain('perTxCapMinor must be <= HARD_MAX_TX_MINOR');
  });
});

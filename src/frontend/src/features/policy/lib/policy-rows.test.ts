import { policy } from '@/test/fixtures';
import { diffPolicies, policyRows, prepareForActivation } from './policy-rows';

describe('policy rows and diff', () => {
  it('renders the active policy as display rows', () => {
    expect(policyRows(policy)).toEqual([
      {k: 'Currency', v: 'USD'},
      {k: 'Per-payment cap', v: '$500.00'},
      {k: 'Ask me above', v: '$25.00'},
      {k: 'Daily budget', v: '$600.00'},
      {k: 'Categories', v: 'developer_tools, events'},
      {k: 'New payees', v: 'Need approval'},
      {k: 'Refunds', v: 'Need approval'},
      {k: 'Speed limit', v: '5 calls / 60 s'},
      {k: 'Approval expires after', v: '15 min'},
    ]);
  });

  it('marks changed rows and warns about removed categories', () => {
    const draft = {...policy, allowedCategories: ['developer_tools']};
    const diff = diffPolicies(policy, draft);
    expect(diff.changeCount).toBe(1);
    const row = diff.rows.find((r) => r.k === 'Categories')!;
    expect(row).toMatchObject({now: 'developer_tools, events', next: 'developer_tools', changed: true});
    expect(diff.warnings[0]).toContain('Removes the events category');
    expect(diff.warnings[0]).toContain('CATEGORY_NOT_ALLOWED');
    expect(diff.warnings.at(-1)).toBe('Your sentence did not change a daily budget, a speed limit or refunds, so the active values were kept.');
  });

  it('warns about loosened limits', () => {
    const diff = diffPolicies(policy, {
      ...policy,
      perTxCapMinor: 90000,
      approvalThresholdMinor: 10000,
      newPayeeRequiresApproval: false
    });
    expect(diff.warnings).toEqual(
      expect.arrayContaining([
        'Raises the per-payment cap from $500.00 to $900.00.',
        'Payments between $25.00 and $100.00 will no longer wait for you.',
        'New payees would no longer need your approval.',
      ]),
    );
  });

  it('treats everything as new when there is no active policy', () => {
    const diff = diffPolicies(null, policy);
    expect(diff.changeCount).toBe(9);
    expect(diff.warnings).toEqual([]);
  });

  it('prepares a draft for activation', () => {
    const prepared = prepareForActivation({...policy, payeeAllowlist: [], currency: null}, policy);
    expect(prepared.version).toBe(2);
    expect(prepared.currency).toBe('USD');
    expect(prepared.payeeAllowlist).toEqual(policy.payeeAllowlist);
  });
});

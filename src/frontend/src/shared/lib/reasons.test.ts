import { policy } from '@/test/fixtures';
import type { ReasonCode } from '../api/types';
import { isReasonCode, policyValuesFor, reasonCopy, REASONS } from './reasons';

const ALL_CODES: ReasonCode[] = [
  'OK_READ_ONLY', 'OK_WITHIN_POLICY', 'AGENT_SUSPENDED', 'NO_ACTIVE_POLICY', 'INVALID_AMOUNT_FORMAT', 'INVALID_CURRENCY',
  'AMOUNT_OVER_CAP', 'DAILY_BUDGET_EXCEEDED', 'VELOCITY_EXCEEDED', 'UNTRUSTED_COUNTERPARTY', 'CATEGORY_NOT_ALLOWED',
  'INVALID_COUNTERPARTY_FORMAT', 'SSRF_BLOCKED', 'NEW_PAYEE_REQUIRES_APPROVAL', 'AMOUNT_REQUIRES_APPROVAL', 'REFUND_REQUIRES_APPROVAL',
];

describe('reason catalog', () => {
  it('covers every engine reason code with label and text', () => {
    for (const code of ALL_CODES) {
      expect(isReasonCode(code)).toBe(true);
      const copy = reasonCopy(code, {amountMinor: 48000, policy});
      expect(copy.label.length).toBeGreaterThan(3);
      expect(copy.text.length).toBeGreaterThan(10);
    }
    expect(Object.keys(REASONS).sort()).toEqual([...ALL_CODES].sort());
  });

  it('fills in the amounts from the call and the policy', () => {
    expect(reasonCopy('AMOUNT_REQUIRES_APPROVAL', {amountMinor: 48000, policy})).toEqual({
      label: 'Over the approval threshold',
      text: 'This payment of $480.00 is above the $25.00 level where you asked to be consulted.',
    });
    expect(reasonCopy('NEW_PAYEE_REQUIRES_APPROVAL', {counterparty: 'newvendor@example.org'}).text).toBe('newvendor@example.org is not on your trusted list yet.');
  });

  it('falls back gracefully for unknown codes', () => {
    expect(reasonCopy('SOMETHING_NEW').label).toBe('something new');
  });

  it('derives the policy values the Why panel shows', () => {
    const values = policyValuesFor(['UNTRUSTED_COUNTERPARTY'], {counterparty: 'pay-me@evil-payments.example', policy});
    expect(values).toEqual(
      expect.arrayContaining([
        {k: 'Source of payee', v: 'kikomo_fetch_url'},
        {k: 'Payee on allowlist', v: 'No'},
        {k: 'Rule that decided', v: 'Deny wins'},
      ]),
    );
    const ok = policyValuesFor(['OK_WITHIN_POLICY'], {counterparty: 'devtools@example.com', policy});
    expect(ok).toEqual([
      {k: 'Per-payment cap', v: '$500.00'},
      {k: 'Approval threshold', v: '$25.00'},
      {k: 'Payee on allowlist', v: 'Yes'},
    ]);
    expect(policyValuesFor(['AGENT_SUSPENDED'], {}, true)).toEqual([
      {k: 'Agent status', v: 'Suspended'},
      {k: 'Rule that decided', v: 'Deny wins'},
    ]);
  });
});

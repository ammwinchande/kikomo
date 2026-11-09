import { auditEntries } from '@/test/fixtures';
import { auditLabel, shortHash } from './audit-events';

describe('audit event labels', () => {
  it('labels each known event type', () => {
    expect(auditLabel(auditEntries[0])).toEqual({event: 'Policy activated', detail: 'cap $500.00'});
    expect(auditLabel(auditEntries[1])).toEqual({event: 'Call decided: allowed', detail: 'send_payment'});
    expect(auditLabel(auditEntries[2])).toEqual({event: 'Payment executed', detail: 'send_payment'});
    expect(auditLabel(auditEntries[3]).event).toBe('Call decided: needs approval');
    expect(auditLabel(auditEntries[4])).toEqual({
      event: 'Call decided: denied',
      detail: 'send_payment · Payee came from a web page'
    });
    expect(auditLabel(auditEntries[5])).toEqual({event: 'Agent suspended', detail: 'Kill switch pressed'});
    expect(auditLabel({
      event_type: 'approval_approved',
      payload: {decided_by: 'admin'}
    })).toEqual({event: 'Approval granted', detail: 'By admin'});
    expect(auditLabel({event_type: 'brand_new_event', payload: {}})).toEqual({event: 'brand new event', detail: ''});
  });
  it('shortens fingerprints', () => {
    expect(shortHash('9f3a1c07abcdef')).toBe('9f3a1c07');
  });
});

/** Query-key factory. Every feature reads its keys from here so invalidation stays consistent. */
export const keys = {
  health: ['health'] as const,
  session: ['auth', 'session'] as const,
  stats: ['stats'] as const,
  approvals: (status?: string) => ['approvals', status ?? 'all'] as const,
  approvalsAll: ['approvals'] as const,
  calls: (agentId?: string) => ['calls', agentId ?? 'all'] as const,
  callsAll: ['calls'] as const,
  agents: ['agents'] as const,
  policy: (agentId: string) => ['policy', agentId] as const,
  policyAll: ['policy'] as const,
  auditEntries: (limit: number) => ['audit', 'entries', limit] as const,
  auditAll: ['audit'] as const,
  demoRun: (id: string) => ['demo', 'run', id] as const,
  demoConnect: ['demo', 'connect'] as const,
};

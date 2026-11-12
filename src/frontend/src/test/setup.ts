import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { setupServer } from 'msw/node';
import { handlers, resetServerState } from './msw/handlers';
import { resetStores } from './render';
import { now } from './fixtures';

export const server = setupServer(...handlers);

beforeAll(() => server.listen({onUnhandledRequest: 'error'}));
beforeEach(() => {
  // Keep approval TTLs aligned with the fixtures without replacing async timers.
  vi.setSystemTime(now());
  resetServerState();
  resetStores();
  document.cookie = 'XSRF-TOKEN=test-xsrf-token';
});
afterEach(() => {
  server.resetHandlers();
  cleanup();
  vi.useRealTimers();
});
afterAll(() => server.close());

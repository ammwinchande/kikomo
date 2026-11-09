import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { setupServer } from 'msw/node';
import { handlers, resetServerState } from './msw/handlers';
import { resetStores } from './render';

export const server = setupServer(...handlers);

beforeAll(() => server.listen({onUnhandledRequest: 'error'}));
beforeEach(() => {
  resetServerState();
  resetStores();
  document.cookie = 'XSRF-TOKEN=test-xsrf-token';
});
afterEach(() => {
  server.resetHandlers();
  cleanup();
});
afterAll(() => server.close());

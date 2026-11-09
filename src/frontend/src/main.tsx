import React from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { queryClient } from './app/query-client';
import { createAppRouter } from './app/router';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={createAppRouter()}/>
    </QueryClientProvider>
  </React.StrictMode>,
);

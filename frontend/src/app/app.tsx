// Root component: creates the browser router from the shared route table.

import { createBrowserRouter, RouterProvider } from 'react-router'
import { routes } from './routes'

const router = createBrowserRouter(routes)

/** Mounts the router; all pages are defined in routes.tsx. */
export function App() {
  return <RouterProvider router={router} />
}

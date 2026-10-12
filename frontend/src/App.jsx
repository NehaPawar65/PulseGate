import { lazy, Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import Layout from './components/Layout.jsx'
import { LoadingState } from './components/ui.jsx'
import LoginPage from './pages/LoginPage.jsx'
import { getAdminToken, onAuthChange } from './services/auth.js'

// Each page (and Recharts with it) loads only when first visited.
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'))
const ClientsPage = lazy(() => import('./pages/ClientsPage.jsx'))
const SimulatorPage = lazy(() => import('./pages/SimulatorPage.jsx'))
const RequestLogPage = lazy(() => import('./pages/RequestLogPage.jsx'))

const page = (Page) => (
  <Suspense fallback={<LoadingState />}>
    <Page />
  </Suspense>
)

export default function App() {
  const [token, setToken] = useState(getAdminToken)
  const [reason, setReason] = useState(null)

  useEffect(
    () =>
      onAuthChange((nextToken, nextReason) => {
        setToken(nextToken)
        setReason(nextReason)
      }),
    [],
  )

  if (!token) return <LoginPage reason={reason} />

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={page(DashboardPage)} />
          <Route path="clients" element={page(ClientsPage)} />
          <Route path="simulator" element={page(SimulatorPage)} />
          <Route path="events" element={page(RequestLogPage)} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

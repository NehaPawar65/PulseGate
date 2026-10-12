import { NavLink, Outlet } from 'react-router'
import { Activity, FlaskConical, Gauge, LogOut, ScrollText, Users } from 'lucide-react'
import { api, API_URL } from '../services/api.js'
import { clearAdminToken } from '../services/auth.js'
import { useApi } from '../hooks/useApi.js'

const NAV = [
  { to: '/', label: 'Dashboard', icon: Gauge, end: true },
  { to: '/clients', label: 'Clients & Policies', icon: Users },
  { to: '/simulator', label: 'Simulator', icon: FlaskConical },
  { to: '/events', label: 'Request Log', icon: ScrollText },
]

export default function Layout() {
  return (
    <div className="min-h-screen md:flex">
      {/* The dark column spans the full page height; the nav inside it stays pinned while scrolling. */}
      <div className="bg-slate-900 md:w-60 md:shrink-0">
        <aside className="flex flex-col text-slate-300 md:sticky md:top-0 md:h-screen">
          <div className="flex items-center gap-2 px-5 py-4 text-white">
            <Activity className="size-6 text-indigo-400" aria-hidden />
            <span className="text-lg font-semibold tracking-tight">PulseGate</span>
            <button
              type="button"
              onClick={() => clearAdminToken()}
              className="ml-auto flex items-center gap-1 rounded px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 hover:text-white"
            >
              <LogOut className="size-3.5" aria-hidden /> Sign out
            </button>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `flex shrink-0 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                    isActive ? 'bg-slate-800 text-white' : 'hover:bg-slate-800/60 hover:text-white'
                  }`
                }
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </NavLink>
            ))}
          </nav>
          <BackendStatus />
        </aside>
      </div>

      <main className="min-w-0 flex-1 px-4 py-6 md:px-8">
        <Outlet />
      </main>
    </div>
  )
}

function BackendStatus() {
  const { data, error, loading } = useApi(api.health, { refreshMs: 10_000 })
  const up = data?.status === 'UP' && !error
  const label = loading ? 'Connecting…' : up ? 'Backend online' : 'Backend offline'
  const dot = loading ? 'bg-slate-500' : up ? 'bg-emerald-400' : 'bg-rose-500'

  return (
    <div className="mt-auto hidden border-t border-slate-800 px-5 py-4 text-xs md:block" title={API_URL}>
      <div className="flex items-center gap-2">
        <span className={`size-2 rounded-full ${dot}`} aria-hidden />
        <span>{label}</span>
      </div>
      <div className="mt-1 truncate text-slate-500">{API_URL}</div>
    </div>
  )
}

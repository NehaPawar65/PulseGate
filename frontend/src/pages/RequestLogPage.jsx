import { useState } from 'react'
import { Trash2 } from 'lucide-react'
import { api } from '../services/api.js'
import { useApi } from '../hooks/useApi.js'
import { formatNumber, formatTime } from '../utils/format.js'
import DecisionBadge, { DECISION_OPTIONS } from '../components/DecisionBadge.jsx'
import { Field, FormError, Select } from '../components/forms.jsx'
import { Button, Card, EmptyState, ErrorState, LoadingState, PageHeader, RefreshButton } from '../components/ui.jsx'

const LIMITS = [50, 100, 200, 500]

export default function RequestLogPage() {
  const [clientId, setClientId] = useState('')
  const [limit, setLimit] = useState(100)
  const [decision, setDecision] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [clearError, setClearError] = useState(null)

  const clients = useApi(api.listClients)
  const events = useApi(() => api.listEvents({ limit, clientId: clientId || undefined }), {
    refreshMs: autoRefresh ? 5_000 : undefined,
    deps: [limit, clientId],
  })

  async function clearHistory() {
    if (!window.confirm('Delete all recorded request events? Client quotas are not affected.')) return
    setClearError(null)
    try {
      await api.clearEvents()
      events.reload()
    } catch (err) {
      setClearError(err)
    }
  }

  // Decision is filtered client-side within the fetched page.
  const rows = (events.data ?? []).filter((e) => !decision || e.decision === decision)

  return (
    <>
      <PageHeader
        title="Request Log"
        description="Every gateway decision, newest first."
        actions={
          <>
            <RefreshButton onClick={events.reload} />
            <Button variant="danger" onClick={clearHistory}><Trash2 className="size-4" aria-hidden /> Clear history</Button>
          </>
        }
      />

      <Card>
        <div className="mb-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Client">
            <Select value={clientId} onChange={(e) => setClientId(e.target.value)}>
              <option value="">All clients</option>
              {(clients.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </Field>
          <Field label="Decision">
            <Select value={decision} onChange={(e) => setDecision(e.target.value)}>
              <option value="">All decisions</option>
              {DECISION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </Select>
          </Field>
          <Field label="Show latest">
            <Select value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
              {LIMITS.map((n) => <option key={n} value={n}>{n} events</option>)}
            </Select>
          </Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-700">
            <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} className="size-4 accent-indigo-600" />
            Auto-refresh every 5 s
          </label>
        </div>
        <FormError error={clearError} />

        {events.loading ? <LoadingState /> : events.error && !events.data ? (
          <ErrorState error={events.error} onRetry={events.reload} />
        ) : rows.length === 0 ? (
          <EmptyState title={events.data.length === 0 ? 'No requests recorded yet' : 'No events match these filters'}>
            {events.data.length === 0 ? 'Run a simulation to generate traffic.' : 'Try a different client or decision.'}
          </EmptyState>
        ) : (
          <>
            {events.error && <p className="mb-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">Showing last loaded events. {events.error.message}</p>}
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="py-2 pr-4 font-medium">Time</th>
                    <th className="py-2 pr-4 font-medium">Client</th>
                    <th className="py-2 pr-4 font-medium">Decision</th>
                    <th className="py-2 pr-4 text-right font-medium">HTTP</th>
                    <th className="py-2 pr-4 text-right font-medium">Latency</th>
                    <th className="py-2 text-right font-medium">Tokens left</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 tabular-nums">
                  {rows.map((e) => (
                    <tr key={e.id}>
                      <td className="py-2 pr-4 text-slate-500">{formatTime(e.timestamp)}</td>
                      <td className="py-2 pr-4 font-medium">{e.clientName ?? <span className="font-normal text-slate-400">unknown key</span>}</td>
                      <td className="py-2 pr-4"><DecisionBadge decision={e.decision} /></td>
                      <td className="py-2 pr-4 text-right font-mono text-xs">{e.httpStatus}</td>
                      <td className="py-2 pr-4 text-right">{(e.latencyMicros / 1000).toFixed(2)} ms</td>
                      <td className="py-2 text-right">{e.remainingTokens ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-3 text-xs text-slate-500">
              Showing {formatNumber(rows.length)} of the latest {formatNumber(events.data.length)} events.
            </p>
          </>
        )}
      </Card>
    </>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Loader2, Play, Trash2 } from 'lucide-react'
import { api } from '../services/api.js'
import { useApi } from '../hooks/useApi.js'
import { getKey } from '../utils/keyStore.js'
import { formatMs, formatNumber, formatPercent } from '../utils/format.js'
import { OUTCOME_COLORS } from '../charts/colors.js'
import StackedBarChart from '../charts/StackedBarChart.jsx'
import { Field, FormError, Input, Select } from '../components/forms.jsx'
import { Button, Card, EmptyState, ErrorState, LoadingState, PageHeader, StatCard } from '../components/ui.jsx'

const PRESETS = [
  { label: 'Small burst', totalRequests: 50, concurrency: 10 },
  { label: 'Heavy burst', totalRequests: 500, concurrency: 50 },
  { label: 'Sequential', totalRequests: 30, concurrency: 1 },
]
const MANUAL = 'manual'
const HISTORY_KEY = 'pulsegate.simulationHistory'

function loadHistory() {
  try {
    return JSON.parse(sessionStorage.getItem(HISTORY_KEY) ?? '[]')
  } catch {
    return []
  }
}

export default function SimulatorPage() {
  const clients = useApi(api.listClients)
  const policies = useApi(api.listPolicies)
  const [history, setHistory] = useState(loadHistory)

  useEffect(() => {
    try {
      sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history))
    } catch {
      // History is a convenience; ignore storage failures.
    }
  }, [history])

  const loading = clients.loading || policies.loading
  const error = clients.error || policies.error

  return (
    <>
      <PageHeader
        title="Traffic Simulator"
        description="Send concurrent requests through the gateway over real HTTP and measure what gets through."
      />
      {loading ? <LoadingState /> : error ? (
        <ErrorState error={error} onRetry={() => { clients.reload(); policies.reload() }} />
      ) : (
        <Simulator
          clients={clients.data}
          policies={policies.data}
          history={history}
          onResult={(run) => setHistory((h) => [run, ...h].slice(0, 10))}
          onClearHistory={() => setHistory([])}
        />
      )}
    </>
  )
}

function Simulator({ clients, policies, history, onResult, onClearHistory }) {
  const usable = clients.filter((c) => c.active && getKey(c.id))
  const [clientChoice, setClientChoice] = useState(usable[0] ? String(usable[0].id) : MANUAL)
  const [manualKey, setManualKey] = useState('')
  const [totalRequests, setTotalRequests] = useState('100')
  const [concurrency, setConcurrency] = useState('10')
  const [running, setRunning] = useState(false)
  const [runError, setRunError] = useState(null)
  const [latest, setLatest] = useState(history[0] ?? null)

  const policyById = Object.fromEntries(policies.map((p) => [p.id, p]))
  const total = Number(totalRequests)
  const workers = Number(concurrency)
  const errors = {
    apiKey: clientChoice === MANUAL && !manualKey.trim() ? 'Paste an API key' : null,
    totalRequests: Number.isInteger(total) && total >= 1 && total <= 5000 ? null : 'Whole number from 1 to 5,000',
    concurrency: Number.isInteger(workers) && workers >= 1 && workers <= 100 ? null : 'Whole number from 1 to 100',
  }
  const valid = !errors.apiKey && !errors.totalRequests && !errors.concurrency

  if (clients.length === 0) {
    return (
      <EmptyState title="No clients to simulate">
        <Link to="/clients" className="font-medium text-indigo-600 hover:underline">Register a client</Link> first.
      </EmptyState>
    )
  }

  async function run(e) {
    e.preventDefault()
    if (!valid) return
    const client = clientChoice === MANUAL ? null : clients.find((c) => String(c.id) === clientChoice)
    const policy = client ? policyById[client.policyId] : null
    setRunning(true)
    setRunError(null)
    try {
      const result = await api.runSimulation({
        apiKey: client ? getKey(client.id) : manualKey.trim(),
        totalRequests: total,
        concurrency: workers,
      })
      const entry = {
        id: Date.now(),
        at: new Date().toISOString(),
        clientName: client?.name ?? 'Pasted key',
        policyLabel: policy ? `${policy.name} · ${policy.algorithm === 'FIXED_WINDOW' ? 'Fixed Window' : 'Token Bucket'}` : '-',
        result,
      }
      setLatest(entry)
      onResult(entry)
    } catch (err) {
      setRunError(err)
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <form onSubmit={run} noValidate className="space-y-4">
          <div className="grid gap-4 md:grid-cols-4">
            <div className="md:col-span-2">
              <Field
                label="Client"
                hint={usable.length < clients.length ? 'Keys created in another browser are not available here; paste one instead.' : undefined}
              >
                <Select value={clientChoice} onChange={(e) => setClientChoice(e.target.value)}>
                  {clients.map((c) => {
                    const hasKey = c.active && getKey(c.id)
                    return (
                      <option key={c.id} value={c.id} disabled={!hasKey}>
                        {c.name} ({c.policyName}){hasKey ? '' : c.active ? ' - key not in this browser' : ' - inactive'}
                      </option>
                    )
                  })}
                  <option value={MANUAL}>Paste an API key…</option>
                </Select>
              </Field>
            </div>
            <Field label="Total requests" error={errors.totalRequests}>
              <Input type="number" min="1" max="5000" value={totalRequests} onChange={(e) => setTotalRequests(e.target.value)} />
            </Field>
            <Field label="Concurrency" error={errors.concurrency} hint="Requests in flight at once">
              <Input type="number" min="1" max="100" value={concurrency} onChange={(e) => setConcurrency(e.target.value)} />
            </Field>
          </div>

          {clientChoice === MANUAL && (
            <Field label="API key" error={manualKey ? errors.apiKey : undefined}>
              <Input value={manualKey} onChange={(e) => setManualKey(e.target.value)} placeholder="pg_…" className="font-mono" autoComplete="off" />
            </Field>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap gap-2">
              {PRESETS.map((p) => (
                <Button
                  key={p.label}
                  variant="secondary"
                  onClick={() => { setTotalRequests(String(p.totalRequests)); setConcurrency(String(p.concurrency)) }}
                >
                  {p.label} <span className="text-slate-400">{p.totalRequests}×{p.concurrency}</span>
                </Button>
              ))}
            </div>
            <Button type="submit" disabled={!valid || running}>
              {running ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <Play className="size-4" aria-hidden />}
              {running ? `Sending ${formatNumber(total)} requests…` : 'Run simulation'}
            </Button>
          </div>
          <FormError error={runError} />
        </form>
      </Card>

      {latest ? <SimulationResultView entry={latest} /> : (
        <EmptyState title="No simulation run yet">Pick a client and a workload, then press Run.</EmptyState>
      )}

      {history.length > 0 && <RunHistory history={history} onClear={onClearHistory} />}
    </div>
  )
}

function SimulationResultView({ entry }) {
  const r = entry.result
  const timeline = r.timeline.map((b) => ({ ...b, label: b.offsetMillis }))
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold">
          {entry.clientName} <span className="text-sm font-normal text-slate-500">{entry.policyLabel}</span>
        </h2>
        <span className="text-sm text-slate-500">
          {formatNumber(r.totalRequests)} requests · concurrency {r.concurrency} · {formatNumber(r.durationMillis)} ms
        </span>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Allowed (200)" value={formatNumber(r.allowed)} swatch={OUTCOME_COLORS.allowed} />
        <StatCard label="Rate limited (429)" value={formatNumber(r.rateLimited)} swatch={OUTCOME_COLORS.rateLimited} />
        <StatCard
          label="Rejection rate"
          value={formatPercent(r.rejectionRate)}
          hint={r.unauthorized || r.errors ? `${r.unauthorized} unauthorized · ${r.errors} errors` : undefined}
        />
        <StatCard label="Throughput" value={`${formatNumber(Math.round(r.throughputPerSecond))}/s`} />
      </div>

      {/* Two charts, each on its own scale: rejections usually outnumber allowed requests
          100:1, which would flatten the allowed pattern (the part that differs by algorithm). */}
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Allowed per 100 ms">
          <StackedBarChart
            data={timeline}
            xKey="label"
            xFormatter={(ms) => `${ms} ms`}
            series={[{ key: 'allowed', label: 'Allowed', color: OUTCOME_COLORS.allowed }]}
            height={200}
            ariaLabel={`${r.allowed} allowed requests over ${r.durationMillis} ms`}
          />
        </Card>
        <Card title="Rejected per 100 ms">
          <StackedBarChart
            data={timeline}
            xKey="label"
            xFormatter={(ms) => `${ms} ms`}
            series={[{ key: 'rejected', label: 'Rejected', color: OUTCOME_COLORS.rateLimited }]}
            height={200}
            ariaLabel={`${r.totalRequests - r.allowed} rejected requests over ${r.durationMillis} ms`}
          />
        </Card>
      </div>

      <Card title="Round-trip latency (client-observed)">
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-5">
          {[['Average', r.latency.avg], ['p50', r.latency.p50], ['p95', r.latency.p95], ['p99', r.latency.p99], ['Max', r.latency.max]].map(
            ([label, ms]) => (
              <div key={label}>
                <dt className="text-xs text-slate-500">{label}</dt>
                <dd className="text-lg font-semibold tabular-nums">{formatMs(ms)}</dd>
              </div>
            ),
          )}
        </dl>
      </Card>
    </div>
  )
}

function RunHistory({ history, onClear }) {
  return (
    <Card>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-700">Recent runs (this browser tab)</h2>
        <Button variant="secondary" onClick={onClear}><Trash2 className="size-4" aria-hidden /> Clear</Button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="py-2 pr-4 font-medium">Time</th>
              <th className="py-2 pr-4 font-medium">Client / policy</th>
              <th className="py-2 pr-4 text-right font-medium">Requests × conc.</th>
              <th className="py-2 pr-4 text-right font-medium">Allowed</th>
              <th className="py-2 pr-4 text-right font-medium">Rejected</th>
              <th className="py-2 pr-4 text-right font-medium">p95</th>
              <th className="py-2 text-right font-medium">Duration</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 tabular-nums">
            {history.map(({ id, at, clientName, policyLabel, result: r }) => (
              <tr key={id}>
                <td className="py-2 pr-4 text-slate-500">{new Date(at).toLocaleTimeString()}</td>
                <td className="py-2 pr-4"><span className="font-medium">{clientName}</span> <span className="text-slate-500">{policyLabel}</span></td>
                <td className="py-2 pr-4 text-right">{r.totalRequests} × {r.concurrency}</td>
                <td className="py-2 pr-4 text-right">{formatNumber(r.allowed)}</td>
                <td className="py-2 pr-4 text-right">{formatNumber(r.totalRequests - r.allowed)}</td>
                <td className="py-2 pr-4 text-right">{formatMs(r.latency.p95)}</td>
                <td className="py-2 text-right">{formatNumber(r.durationMillis)} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

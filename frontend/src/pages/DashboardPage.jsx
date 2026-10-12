import { Link } from 'react-router'
import { api } from '../services/api.js'
import { useApi } from '../hooks/useApi.js'
import { formatMicrosAsMs, formatNumber, formatPercent } from '../utils/format.js'
import TrafficTimelineChart from '../charts/TrafficTimelineChart.jsx'
import { OUTCOME_COLORS } from '../charts/colors.js'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, RefreshButton, StatCard } from '../components/ui.jsx'

const REFRESH_MS = 5_000

export default function DashboardPage() {
  const { data: summary, error, loading, reload } = useApi(api.analyticsSummary, { refreshMs: REFRESH_MS })

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Live gateway traffic from recorded request events. Refreshes every 5 seconds."
        actions={<RefreshButton onClick={reload} />}
      />
      <DashboardBody summary={summary} error={error} loading={loading} reload={reload} />
    </>
  )
}

function DashboardBody({ summary, error, loading, reload }) {
  if (loading) return <LoadingState label="Loading analytics…" />
  if (error && !summary) return <ErrorState error={error} onRetry={reload} />

  if (summary.totalRequests === 0) {
    return (
      <EmptyState title="No traffic recorded yet">
        Register a client, then <Link to="/simulator" className="font-medium text-indigo-600 hover:underline">run a simulation</Link> to
        generate requests.
      </EmptyState>
    )
  }

  const latency = summary.gatewayLatencyMicros
  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">
          Showing last known numbers. {error.message}
        </p>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total requests" value={formatNumber(summary.totalRequests)} />
        <StatCard label="Allowed" value={formatNumber(summary.allowed)} swatch={OUTCOME_COLORS.allowed} />
        <StatCard
          label="Rejected"
          value={formatNumber(summary.rateLimited + summary.invalidKey)}
          hint={`${formatNumber(summary.rateLimited)} rate limited · ${formatNumber(summary.invalidKey)} invalid key`}
          swatch={OUTCOME_COLORS.rateLimited}
        />
        <StatCard label="Rejection rate" value={formatPercent(summary.rejectionRate)} />
      </div>

      <Card title="Traffic, last hour (requests per minute)">
        <TrafficTimelineChart points={summary.lastHour} />
      </Card>

      <Card title="Gateway latency (server-side)">
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-5">
          {[
            ['Average', latency.avg],
            ['p50', latency.p50],
            ['p95', latency.p95],
            ['p99', latency.p99],
            ['Max', latency.max],
          ].map(([label, micros]) => (
            <div key={label}>
              <dt className="text-xs text-slate-500">{label}</dt>
              <dd className="text-lg font-semibold tabular-nums">{formatMicrosAsMs(micros)}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <Card title="Per client">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-medium">Client</th>
                <th className="py-2 pr-4 text-right font-medium">Allowed</th>
                <th className="py-2 pr-4 text-right font-medium">Rate limited</th>
                <th className="py-2 text-right font-medium">Rejection rate</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 tabular-nums">
              {summary.clients.map((c) => (
                <tr key={c.clientId}>
                  <td className="py-2 pr-4 font-medium">{c.clientName}</td>
                  <td className="py-2 pr-4 text-right">{formatNumber(c.allowed)}</td>
                  <td className="py-2 pr-4 text-right">{formatNumber(c.rateLimited)}</td>
                  <td className="py-2 text-right">{formatPercent(c.rejectionRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}

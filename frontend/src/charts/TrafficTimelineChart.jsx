import StackedBarChart from './StackedBarChart.jsx'
import { OUTCOME_COLORS } from './colors.js'

const MINUTE = 60_000
const WINDOW_MINUTES = 60

const timeLabel = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

/**
 * Per-minute outcomes for the last hour. The API returns only minutes that had
 * traffic; empty minutes are filled with zeros so idle periods are visible.
 */
export default function TrafficTimelineChart({ points }) {
  const byMinute = new Map(points.map((p) => [Date.parse(p.minute), p]))
  const end = Math.floor(Date.now() / MINUTE) * MINUTE
  const data = []
  for (let t = end - (WINDOW_MINUTES - 1) * MINUTE; t <= end; t += MINUTE) {
    const p = byMinute.get(t)
    data.push({ t, allowed: p?.allowed ?? 0, rateLimited: p?.rateLimited ?? 0, invalidKey: p?.invalidKey ?? 0 })
  }
  const totals = data.reduce(
    (acc, d) => ({ allowed: acc.allowed + d.allowed, rejected: acc.rejected + d.rateLimited + d.invalidKey }),
    { allowed: 0, rejected: 0 },
  )

  return (
    <StackedBarChart
      data={data}
      xKey="t"
      xFormatter={timeLabel}
      series={[
        { key: 'allowed', label: 'Allowed', color: OUTCOME_COLORS.allowed },
        { key: 'rateLimited', label: 'Rate limited', color: OUTCOME_COLORS.rateLimited },
        { key: 'invalidKey', label: 'Invalid key', color: OUTCOME_COLORS.invalidKey },
      ]}
      ariaLabel={`Last hour: ${totals.allowed} allowed and ${totals.rejected} rejected requests`}
    />
  )
}

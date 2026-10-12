import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AXIS_TEXT, GRID_COLOR } from './colors.js'

/**
 * Stacked bars over an ordered x axis.
 * series: [{ key, label, color }] in stack order (first = bottom).
 * Legend and tooltip keep that order; text stays in ink colors and a swatch carries identity.
 */
export default function StackedBarChart({ data, xKey, xFormatter, series, height = 260, ariaLabel }) {
  const last = series.length - 1
  const order = Object.fromEntries(series.map((s, i) => [s.key, i]))
  const byOrder = (item) => order[item.dataKey] ?? 0

  return (
    <div role="img" aria-label={ariaLabel} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -12 }} barCategoryGap={2}>
          <CartesianGrid vertical={false} stroke={GRID_COLOR} />
          <XAxis
            dataKey={xKey}
            tickFormatter={xFormatter}
            tick={{ fontSize: 12, fill: AXIS_TEXT }}
            tickLine={false}
            axisLine={{ stroke: GRID_COLOR }}
            minTickGap={24}
          />
          <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: AXIS_TEXT }} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ fill: '#f1f5f9' }}
            content={<ChartTooltip series={series} xFormatter={xFormatter} />}
          />
          {/* A single series is named by the card title; a legend box would only repeat it. */}
          {series.length > 1 && (
            <Legend
              iconType="square"
              iconSize={10}
              itemSorter={byOrder}
              wrapperStyle={{ fontSize: 12 }}
              formatter={(value) => <span style={{ color: AXIS_TEXT }}>{value}</span>}
            />
          )}
          {series.map((s, i) => (
            <Bar
              key={s.key}
              dataKey={s.key}
              name={s.label}
              stackId="outcome"
              fill={s.color}
              stroke="#ffffff"
              strokeWidth={1}
              radius={i === last ? [4, 4, 0, 0] : 0}
              maxBarSize={48}
              isAnimationActive={false}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function ChartTooltip({ active, payload, label, series, xFormatter }) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <div className="mb-1 font-medium text-slate-900">{xFormatter ? xFormatter(label) : label}</div>
      {/* Top of the stack first, matching what the eye sees. */}
      {[...series].reverse().map((s) => (
        <div key={s.key} className="flex items-center gap-2 text-slate-600">
          <span className="size-2.5 rounded-sm" style={{ backgroundColor: s.color }} aria-hidden />
          <span className="flex-1">{s.label}</span>
          <span className="font-medium tabular-nums text-slate-900">{row[s.key]}</span>
        </div>
      ))}
    </div>
  )
}

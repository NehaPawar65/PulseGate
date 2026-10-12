const integer = new Intl.NumberFormat('en-US')

export const formatNumber = (n) => integer.format(n ?? 0)

/** 0.952 -> "95.2%" */
export const formatPercent = (ratio) => `${((ratio ?? 0) * 100).toFixed(1)}%`

/** Gateway latency arrives in microseconds; show milliseconds. */
export const formatMicrosAsMs = (micros) => `${((micros ?? 0) / 1000).toFixed(1)} ms`

export const formatMs = (ms) => `${(ms ?? 0).toFixed(1)} ms`

export const formatTime = (iso) =>
  new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })

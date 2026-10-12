// One color per outcome, used everywhere so a color always means the same thing.
// Validated (dataviz validate_palette.js, light surface): CVD ΔE >= 31, contrast >= 3:1.
// Blue/orange instead of green/red: green vs red fails deuteranopia separation (ΔE 5.8).
export const OUTCOME_COLORS = {
  allowed: '#2563eb',
  rateLimited: '#e8590c',
  invalidKey: '#a855f7',
}

export const GRID_COLOR = '#e2e8f0'
export const AXIS_TEXT = '#64748b'

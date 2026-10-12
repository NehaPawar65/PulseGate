import { OUTCOME_COLORS } from '../charts/colors.js'

const DECISIONS = {
  ALLOWED: { label: 'Allowed', color: OUTCOME_COLORS.allowed },
  REJECTED_RATE_LIMIT: { label: 'Rate limited', color: OUTCOME_COLORS.rateLimited },
  REJECTED_INVALID_KEY: { label: 'Invalid key', color: OUTCOME_COLORS.invalidKey },
}

/** Color swatch + text label, so the decision never relies on color alone. */
export default function DecisionBadge({ decision }) {
  const d = DECISIONS[decision] ?? { label: decision, color: '#94a3b8' }
  return (
    <span className="inline-flex items-center gap-1.5 text-sm text-slate-700">
      <span className="size-2.5 rounded-sm" style={{ backgroundColor: d.color }} aria-hidden />
      {d.label}
    </span>
  )
}

export const DECISION_OPTIONS = Object.entries(DECISIONS).map(([value, { label }]) => ({ value, label }))

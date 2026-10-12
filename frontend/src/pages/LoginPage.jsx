import { useEffect, useState } from 'react'
import { Activity, LogIn } from 'lucide-react'
import { api } from '../services/api.js'
import { setAdminToken } from '../services/auth.js'
import { Field, FormError, Input } from '../components/forms.jsx'
import { Button } from '../components/ui.jsx'

export default function LoginPage({ reason }) {
  const [token, setToken] = useState('')
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    document.title = 'Sign in · PulseGate'
  }, [])

  async function submit(e) {
    e.preventDefault()
    const value = token.trim()
    if (!value) return
    setChecking(true)
    setError(null)
    try {
      await api.verifyAdminToken(value)
      setAdminToken(value)
    } catch (err) {
      setError(err.status === 401 ? new Error('That admin token is not valid.') : err)
      setChecking(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-5 flex items-center gap-2">
          <Activity className="size-6 text-indigo-600" aria-hidden />
          <h1 className="text-xl font-semibold tracking-tight">PulseGate admin</h1>
        </div>
        {reason && (
          <p className="mb-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">{reason}</p>
        )}
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field
            label="Admin token"
            hint="The value of PULSEGATE_ADMIN_TOKEN, or the token printed in the backend console at startup."
          >
            <Input
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              autoFocus
              autoComplete="current-password"
              className="font-mono"
            />
          </Field>
          <FormError error={error} />
          <Button type="submit" disabled={!token.trim() || checking} className="w-full">
            <LogIn className="size-4" aria-hidden /> {checking ? 'Checking…' : 'Sign in'}
          </Button>
        </form>
        <p className="mt-4 text-xs text-slate-500">The token is kept only for this browser tab and is cleared when you close it.</p>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { Check, Copy, KeyRound, Pencil, Plus } from 'lucide-react'
import { api } from '../services/api.js'
import { useApi } from '../hooks/useApi.js'
import { rememberKey } from '../utils/keyStore.js'
import Modal from '../components/Modal.jsx'
import { Field, FormError, Input, Select } from '../components/forms.jsx'
import { Button, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../components/ui.jsx'

const ALGORITHM_LABELS = { TOKEN_BUCKET: 'Token Bucket', FIXED_WINDOW: 'Fixed Window' }

export default function ClientsPage() {
  const policies = useApi(api.listPolicies)
  const clients = useApi(api.listClients)

  return (
    <>
      <PageHeader title="Clients & Policies" description="Configure rate-limit policies and register API clients." />
      <div className="space-y-6">
        <PoliciesSection {...policies} onChanged={() => { policies.reload(); clients.reload() }} />
        <ClientsSection {...clients} policies={policies.data ?? []} />
      </div>
    </>
  )
}

/* ---------------------------------- Policies ---------------------------------- */

function PoliciesSection({ data: policies, error, loading, reload, onChanged }) {
  const [editing, setEditing] = useState(null) // null | 'new' | policy

  return (
    <Card>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-700">Policies</h2>
        <Button onClick={() => setEditing('new')}><Plus className="size-4" aria-hidden /> New policy</Button>
      </div>

      {loading ? <LoadingState /> : error ? <ErrorState error={error} onRetry={reload} /> : policies.length === 0 ? (
        <EmptyState title="No policies yet">Create one to define how many requests a client may send.</EmptyState>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-medium">Name</th>
                <th className="py-2 pr-4 font-medium">Algorithm</th>
                <th className="py-2 pr-4 text-right font-medium">Capacity</th>
                <th className="py-2 pr-4 text-right font-medium">Refill / window</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 tabular-nums">
              {policies.map((p) => (
                <tr key={p.id}>
                  <td className="py-2 pr-4 font-medium">{p.name}</td>
                  <td className="py-2 pr-4">{ALGORITHM_LABELS[p.algorithm]}</td>
                  <td className="py-2 pr-4 text-right">{p.capacity}</td>
                  <td className="py-2 pr-4 text-right">{describeRate(p)}</td>
                  <td className="py-2 text-right">
                    <Button variant="secondary" onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}>
                      <Pencil className="size-3.5" aria-hidden /> Edit
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'New policy' : `Edit policy “${editing.name}”`} onClose={() => setEditing(null)}>
          <PolicyForm
            policy={editing === 'new' ? null : editing}
            onCancel={() => setEditing(null)}
            onSaved={() => { setEditing(null); onChanged() }}
          />
        </Modal>
      )}
    </Card>
  )
}

function describeRate(p) {
  return p.algorithm === 'FIXED_WINDOW' ? `per ${p.windowSeconds}s window` : `${p.refillTokensPerSecond} tokens/s`
}

function validatePolicy(form, isNew) {
  const errors = {}
  if (isNew && !form.name.trim()) errors.name = 'Name is required'
  const capacity = Number(form.capacity)
  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 100000) errors.capacity = 'Whole number from 1 to 100,000'
  if (form.algorithm === 'TOKEN_BUCKET') {
    const rate = Number(form.refillTokensPerSecond)
    if (!(rate > 0) || rate > 100000) errors.refillTokensPerSecond = 'Must be greater than 0'
  } else {
    const window = Number(form.windowSeconds)
    if (!Number.isInteger(window) || window < 1 || window > 86400) errors.windowSeconds = 'Whole seconds from 1 to 86,400'
  }
  return errors
}

function PolicyForm({ policy, onCancel, onSaved }) {
  const isNew = policy === null
  const [form, setForm] = useState({
    name: policy?.name ?? '',
    algorithm: policy?.algorithm ?? 'TOKEN_BUCKET',
    capacity: String(policy?.capacity ?? 10),
    refillTokensPerSecond: String(policy?.refillTokensPerSecond || 1),
    windowSeconds: String(policy?.windowSeconds ?? 60),
  })
  const [touched, setTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const [serverError, setServerError] = useState(null)

  const errors = validatePolicy(form, isNew)
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setTouched(true)
    if (Object.keys(errors).length) return
    const body = {
      algorithm: form.algorithm,
      capacity: Number(form.capacity),
      refillTokensPerSecond: form.algorithm === 'TOKEN_BUCKET' ? Number(form.refillTokensPerSecond) : null,
      windowSeconds: form.algorithm === 'FIXED_WINDOW' ? Number(form.windowSeconds) : null,
    }
    setSaving(true)
    setServerError(null)
    try {
      if (isNew) await api.createPolicy({ name: form.name.trim(), ...body })
      else await api.updatePolicy(policy.id, body)
      onSaved()
    } catch (err) {
      setServerError(err)
    } finally {
      setSaving(false)
    }
  }

  const show = (key) => (touched ? errors[key] : undefined)

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Field label="Name" error={show('name')} hint={isNew ? 'e.g. free, pro' : 'Names cannot be changed'}>
        <Input value={form.name} onChange={set('name')} disabled={!isNew} autoFocus={isNew} maxLength={50} />
      </Field>
      <Field label="Algorithm">
        <Select value={form.algorithm} onChange={set('algorithm')}>
          <option value="TOKEN_BUCKET">Token Bucket: smooth refill, allows bursts</option>
          <option value="FIXED_WINDOW">Fixed Window: N requests per window</option>
        </Select>
      </Field>
      <Field
        label="Capacity"
        error={show('capacity')}
        hint={form.algorithm === 'TOKEN_BUCKET' ? 'Maximum burst size (bucket size)' : 'Requests allowed per window'}
      >
        <Input type="number" min="1" value={form.capacity} onChange={set('capacity')} autoFocus={!isNew} />
      </Field>
      {form.algorithm === 'TOKEN_BUCKET' ? (
        <Field label="Refill rate (tokens per second)" error={show('refillTokensPerSecond')} hint="Sustained request rate">
          <Input type="number" min="0" step="any" value={form.refillTokensPerSecond} onChange={set('refillTokensPerSecond')} />
        </Field>
      ) : (
        <Field label="Window length (seconds)" error={show('windowSeconds')}>
          <Input type="number" min="1" value={form.windowSeconds} onChange={set('windowSeconds')} />
        </Field>
      )}
      {!isNew && <p className="text-xs text-slate-500">Clients on this policy get a fresh quota with the new limits on their next request.</p>}
      <FormError error={serverError} />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create policy' : 'Save changes'}</Button>
      </div>
    </form>
  )
}

/* ---------------------------------- Clients ---------------------------------- */

function ClientsSection({ data: clients, error, loading, reload, policies }) {
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState(null) // { client, apiKey } shown once

  return (
    <Card>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-700">Clients</h2>
        <Button onClick={() => setCreating(true)} disabled={policies.length === 0} title={policies.length === 0 ? 'Create a policy first' : undefined}>
          <Plus className="size-4" aria-hidden /> Register client
        </Button>
      </div>

      {loading ? <LoadingState /> : error ? <ErrorState error={error} onRetry={reload} /> : clients.length === 0 ? (
        <EmptyState title="No clients yet">
          {policies.length === 0 ? 'Create a policy first, then register a client.' : 'Register a client to get an API key.'}
        </EmptyState>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-medium">Name</th>
                <th className="py-2 pr-4 font-medium">API key</th>
                <th className="py-2 pr-4 font-medium">Policy</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 font-medium">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {clients.map((c) => (
                <tr key={c.id}>
                  <td className="py-2 pr-4 font-medium">{c.name}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-slate-600">{c.apiKeyPrefix}…</td>
                  <td className="py-2 pr-4">{c.policyName}</td>
                  <td className="py-2 pr-4">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${c.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>
                      {c.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="py-2 text-slate-500">{new Date(c.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {creating && (
        <Modal title="Register client" onClose={() => setCreating(false)}>
          <ClientForm
            policies={policies}
            onCancel={() => setCreating(false)}
            onCreated={(result) => {
              rememberKey(result.client.id, result.apiKey)
              setCreating(false)
              setCreated(result)
              reload()
            }}
          />
        </Modal>
      )}
      {created && <KeyRevealDialog result={created} onClose={() => setCreated(null)} />}
    </Card>
  )
}

function ClientForm({ policies, onCancel, onCreated }) {
  const [name, setName] = useState('')
  const [policyId, setPolicyId] = useState(String(policies[0]?.id ?? ''))
  const [touched, setTouched] = useState(false)
  const [saving, setSaving] = useState(false)
  const [serverError, setServerError] = useState(null)
  const nameError = name.trim() ? null : 'Name is required'

  async function submit(e) {
    e.preventDefault()
    setTouched(true)
    if (nameError) return
    setSaving(true)
    setServerError(null)
    try {
      onCreated(await api.createClient({ name: name.trim(), policyId: Number(policyId) }))
    } catch (err) {
      setServerError(err)
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Field label="Client name" error={touched ? nameError : undefined} hint="e.g. mobile-app, partner-api">
        <Input value={name} onChange={(e) => setName(e.target.value)} autoFocus maxLength={100} />
      </Field>
      <Field label="Policy">
        <Select value={policyId} onChange={(e) => setPolicyId(e.target.value)}>
          {policies.map((p) => (
            <option key={p.id} value={p.id}>{p.name} ({p.capacity}, {describeRate(p)})</option>
          ))}
        </Select>
      </Field>
      <FormError error={serverError} />
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={saving}>{saving ? 'Registering…' : 'Register'}</Button>
      </div>
    </form>
  )
}

function KeyRevealDialog({ result, onClose }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(result.apiKey)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <Modal title="API key created" onClose={onClose}>
      <div className="space-y-4">
        <div className="flex items-start gap-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          <KeyRound className="mt-0.5 size-4 shrink-0" aria-hidden />
          <p>
            Copy this key for <strong>{result.client.name}</strong> now. PulseGate stores only a hash, so it
            <strong> cannot show this key again</strong>.
          </p>
        </div>
        <div className="flex gap-2">
          <code className="min-w-0 flex-1 break-all rounded-md border border-slate-200 bg-slate-50 px-3 py-2 font-mono text-xs">
            {result.apiKey}
          </code>
          <Button variant="secondary" onClick={copy} aria-label="Copy API key">
            {copied ? <Check className="size-4 text-emerald-600" aria-hidden /> : <Copy className="size-4" aria-hidden />}
          </Button>
        </div>
        <p className="text-xs text-slate-500">This browser also remembers the key so the Simulator can use it.</p>
        <div className="flex justify-end">
          <Button onClick={onClose}>Done</Button>
        </div>
      </div>
    </Modal>
  )
}

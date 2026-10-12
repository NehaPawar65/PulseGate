// Single place for every backend call. Components never call fetch directly.

export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request(path, { method = 'GET', body } = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    // fetch only rejects on network failure (server down, CORS blocked, DNS).
    throw new ApiError(`Cannot reach the PulseGate backend at ${API_URL}. Is it running?`, 0)
  }

  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    // Backend errors are RFC 9457 ProblemDetail: { status, title, detail }.
    throw new ApiError(data?.detail ?? `Request failed with status ${response.status}`, response.status)
  }
  return data
}

export const api = {
  health: () => request('/api/health'),

  listPolicies: () => request('/api/policies'),
  createPolicy: (policy) => request('/api/policies', { method: 'POST', body: policy }),
  updatePolicy: (id, policy) => request(`/api/policies/${id}`, { method: 'PUT', body: policy }),

  listClients: () => request('/api/clients'),
  createClient: (client) => request('/api/clients', { method: 'POST', body: client }),

  analyticsSummary: () => request('/api/analytics/summary'),

  listEvents: ({ limit = 50, clientId } = {}) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (clientId) params.set('clientId', String(clientId))
    return request(`/api/events?${params}`)
  },
  clearEvents: () => request('/api/events', { method: 'DELETE' }),

  runSimulation: (simulation) => request('/api/simulations', { method: 'POST', body: simulation }),
}

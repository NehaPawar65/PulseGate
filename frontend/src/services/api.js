// Single place for every backend call. Components never call fetch directly.
import { clearAdminToken, getAdminToken } from './auth.js'

export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * auth: send the admin token (all admin APIs). token: use this token instead of the stored one
 * (sign-in check), and don't sign out on 401 since nothing is stored yet.
 */
async function request(path, { method = 'GET', body, auth = true, token } = {}) {
  const headers = {}
  if (body) headers['Content-Type'] = 'application/json'
  const adminToken = token ?? (auth ? getAdminToken() : null)
  if (adminToken) headers.Authorization = `Bearer ${adminToken}`

  let response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    // fetch only rejects on network failure (server down, CORS blocked, DNS).
    throw new ApiError(`Cannot reach the PulseGate backend at ${API_URL}. Is it running?`, 0)
  }

  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && auth && !token) {
      clearAdminToken('Your admin token was rejected. Please sign in again.')
    }
    // Backend errors are RFC 9457 ProblemDetail: { status, title, detail }.
    throw new ApiError(data?.detail ?? `Request failed with status ${response.status}`, response.status)
  }
  return data
}

export const api = {
  health: () => request('/api/health', { auth: false }),

  /** Resolves if the token is valid for the admin APIs; throws ApiError (401) if not. */
  verifyAdminToken: (token) => request('/api/policies', { token }),

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

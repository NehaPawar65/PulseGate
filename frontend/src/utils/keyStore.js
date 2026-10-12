// Local-demo convenience: remembers API keys created in this browser so the
// simulator can offer a client picker. The backend only stores hashes, so a key
// lost here cannot be recovered. Never do this with production credentials.

const STORAGE_KEY = 'pulsegate.apiKeys'

function readAll() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
  } catch {
    return {}
  }
}

export function rememberKey(clientId, apiKey) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...readAll(), [clientId]: apiKey }))
  } catch {
    // Storage unavailable (private mode, quota): the key is still shown once in the UI.
  }
}

export function getKey(clientId) {
  return readAll()[clientId] ?? null
}

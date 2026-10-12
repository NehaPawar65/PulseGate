// Admin token for the dashboard session. sessionStorage: cleared when the tab closes.
// Falls back to memory if storage is unavailable (private mode, blocked site data).

const STORAGE_KEY = 'pulsegate.adminToken'
let memoryToken = null
const listeners = new Set()

export function getAdminToken() {
  try {
    return sessionStorage.getItem(STORAGE_KEY) ?? memoryToken
  } catch {
    return memoryToken
  }
}

export function setAdminToken(token) {
  memoryToken = token
  try {
    sessionStorage.setItem(STORAGE_KEY, token)
  } catch {
    // memoryToken still holds it for this page load
  }
  notify(null)
}

/** reason: optional message shown on the sign-in screen (e.g. token rejected). */
export function clearAdminToken(reason = null) {
  memoryToken = null
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // nothing stored
  }
  notify(reason)
}

export function onAuthChange(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function notify(reason) {
  listeners.forEach((listener) => listener(getAdminToken(), reason))
}

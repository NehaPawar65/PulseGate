import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Loads data from an api.* function and tracks loading / error state.
 *
 * - `loading` is true only until the first response, so background refreshes don't flash spinners.
 * - `refreshMs` re-fetches on an interval (e.g. live dashboard numbers).
 * - `deps` re-fetches when any value changes (e.g. filters); keep the array the same length.
 * - Responses that arrive after unmount or after a newer request are ignored.
 */
export function useApi(fetcher, { refreshMs, deps = [] } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher
  const requestId = useRef(0)

  const load = useCallback(async () => {
    const id = ++requestId.current
    try {
      const data = await fetcherRef.current()
      if (id === requestId.current) setState({ data, error: null, loading: false })
    } catch (error) {
      if (id === requestId.current) setState((s) => ({ ...s, error, loading: false }))
    }
  }, [])

  useEffect(() => {
    load()
    if (!refreshMs) return () => { requestId.current++ }
    const timer = setInterval(load, refreshMs)
    return () => {
      clearInterval(timer)
      requestId.current++
    }
  }, [load, refreshMs, ...deps]) // eslint-disable-line react-hooks/exhaustive-deps

  return { ...state, reload: load }
}

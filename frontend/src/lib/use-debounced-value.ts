// Debounce hook for search inputs.

import { useEffect, useState } from 'react'

/** Delays a fast-changing value (e.g. a search box) so the API isn't called on every keystroke. */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

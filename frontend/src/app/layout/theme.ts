import { useCallback, useState } from 'react'

type Theme = 'light' | 'dark'
const STORAGE_KEY = 'ef-theme'

function readTheme(): Theme {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** Light by default; the choice is a per-browser convenience, so localStorage is the right place for it. */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(readTheme)

  const toggle = useCallback(() => {
    setTheme((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark'
      document.documentElement.classList.toggle('dark', next === 'dark')
      try {
        localStorage.setItem(STORAGE_KEY, next)
      } catch {
        // Storage can be unavailable (private mode); the toggle still works for this page view.
      }
      return next
    })
  }, [])

  return { theme, toggle }
}

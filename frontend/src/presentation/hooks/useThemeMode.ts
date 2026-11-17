import { useCallback, useEffect, useState } from 'react'
import type { ThemePreferencesUseCases } from '../../application/ports.ts'
import type { ResolvedTheme, ThemeMode } from '../../domain/types.ts'

function readSystemDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export interface UseThemeModeResult {
  themeMode: ThemeMode
  resolvedTheme: ResolvedTheme
  setThemeMode: (mode: ThemeMode) => void
}

export function useThemeMode(themePreferences?: ThemePreferencesUseCases): UseThemeModeResult {
  const [themeMode, setThemeModeState] = useState<ThemeMode>(() => themePreferences?.read?.() || 'system')
  const [systemDark, setSystemDark] = useState<boolean>(readSystemDark)

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    media.addEventListener?.('change', onChange)
    return () => media.removeEventListener?.('change', onChange)
  }, [])

  const resolvedTheme: ResolvedTheme = themeMode === 'system' ? (systemDark ? 'dark' : 'light') : themeMode

  useEffect(() => {
    if (typeof document === 'undefined') return
    document.documentElement.dataset.theme = resolvedTheme
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute('content', resolvedTheme === 'dark' ? '#000000' : '#ffffff')
    }
  }, [resolvedTheme])

  const setThemeMode = useCallback((mode: ThemeMode) => {
    const valid: ThemeMode = mode === 'light' || mode === 'dark' || mode === 'system' ? mode : 'system'
    setThemeModeState(valid)
    themePreferences?.write?.(valid)
  }, [themePreferences])

  return { themeMode, resolvedTheme, setThemeMode }
}

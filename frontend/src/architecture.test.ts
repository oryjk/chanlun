import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'bun:test'

const SRC_DIR = path.dirname(fileURLToPath(import.meta.url))

interface LayerRule {
  match: (file: string) => boolean
  forbidden: string[]
}

const LAYER_FORBIDDEN: LayerRule[] = [
  {
    match: (file) => file.startsWith('domain/'),
    forbidden: ['application/', 'infrastructure/', 'presentation/', 'app/', 'react', 'react-dom', 'echarts'],
  },
  {
    match: (file) => file.startsWith('application/'),
    forbidden: ['infrastructure/', 'presentation/', 'app/', 'react', 'react-dom'],
  },
  {
    match: (file) => file.startsWith('infrastructure/'),
    forbidden: ['presentation/', 'app/'],
  },
  {
    match: (file) => file.startsWith('presentation/') || file === 'App.tsx' || file === 'App.jsx',
    forbidden: ['infrastructure/', 'app/'],
  },
]

function isTestFile(file: string): boolean {
  return (
    file.endsWith('.test.ts') ||
    file.endsWith('.test.tsx') ||
    file.endsWith('.test.js') ||
    file.endsWith('.test.jsx')
  )
}

function isScriptFile(name: string): boolean {
  return (
    name.endsWith('.ts') ||
    name.endsWith('.tsx') ||
    name.endsWith('.js') ||
    name.endsWith('.jsx')
  )
}

function extractImports(source: string): string[] {
  const imports: string[] = []
  const pattern = /\bfrom\s+['"]([^'"]+)['"]|\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)|\bimport\s+['"]([^'"]+)['"]/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(source)) !== null) {
    const specifier = match[1] ?? match[2] ?? match[3]
    if (specifier) imports.push(specifier)
  }
  return imports
}

function resolveImport(fromFile: string, specifier: string): string {
  if (!specifier.startsWith('.')) {
    return specifier
  }
  return path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), specifier))
}

function isForbiddenTarget(target: string, forbiddenRule: string): boolean {
  if (forbiddenRule.endsWith('/')) {
    return target === forbiddenRule.slice(0, -1) || target.startsWith(forbiddenRule)
  }
  return target === forbiddenRule || target.startsWith(`${forbiddenRule}/`)
}

export function findViolations(sources: Record<string, string>): string[] {
  const violations: string[] = []
  for (const [file, content] of Object.entries(sources)) {
    if (isTestFile(file)) {
      continue
    }
    const rule = LAYER_FORBIDDEN.find((item) => item.match(file))
    const forbidden = rule ? rule.forbidden : []
    for (const specifier of extractImports(content)) {
      const target = resolveImport(file, specifier)
      if (
        isTestFile(target) ||
        forbidden.some((forbiddenRule) => isForbiddenTarget(target, forbiddenRule))
      ) {
        violations.push(`${file} imports ${target}`)
      }
    }
  }
  return violations
}

function loadSrcSources(dir = SRC_DIR, prefix = ''): Record<string, string> {
  const sources: Record<string, string> = {}
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const relPath = prefix ? `${prefix}/${entry.name}` : entry.name
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      Object.assign(sources, loadSrcSources(fullPath, relPath))
    } else if (entry.isFile() && isScriptFile(entry.name)) {
      sources[relPath] = readFileSync(fullPath, 'utf-8')
    }
  }
  return sources
}

describe('frontend architecture guard', () => {
  test('guard flags presentation importing infrastructure', () => {
    expect(
      findViolations({
        'presentation/components/X.tsx': "import { a } from '../../infrastructure/api/marketGateway.ts'",
      }),
    ).toEqual(['presentation/components/X.tsx imports infrastructure/api/marketGateway.ts'])
  })

  test('guard flags side-effect import of infrastructure', () => {
    expect(
      findViolations({
        'presentation/components/X.tsx': "import '../../infrastructure/api/marketGateway.ts'",
      }),
    ).toEqual(['presentation/components/X.tsx imports infrastructure/api/marketGateway.ts'])
  })

  test('guard flags domain importing react-dom/client', () => {
    expect(
      findViolations({
        'domain/x.ts': "import { createRoot } from 'react-dom/client'",
      }),
    ).toEqual(['domain/x.ts imports react-dom/client'])
  })

  test('src uses TypeScript only', () => {
    const legacyFiles = Object.keys(loadSrcSources()).filter(
      (file) => file.endsWith('.js') || file.endsWith('.jsx'),
    )
    expect(legacyFiles).toEqual([])
  })

  test('src layers depend inward', () => {
    expect(findViolations(loadSrcSources())).toEqual([])
  })

  test('only infrastructure calls fetch', () => {
    const offenders = Object.entries(loadSrcSources())
      .filter(
        ([file, content]) =>
          !file.startsWith('infrastructure/') &&
          !isTestFile(file) &&
          /\bfetch\s*\(/.test(content),
      )
      .map(([file]) => file)
    expect(offenders).toEqual([])
  })
})

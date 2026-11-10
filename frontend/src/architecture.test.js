import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, test } from 'bun:test'

const SRC_DIR = path.dirname(fileURLToPath(import.meta.url))

const LAYER_FORBIDDEN = [
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
    match: (file) => file.startsWith('presentation/') || file === 'App.jsx',
    forbidden: ['infrastructure/', 'app/'],
  },
]

function extractImports(source) {
  const imports = []
  const pattern = /\bfrom\s+['"]([^'"]+)['"]|\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)|\bimport\s+['"]([^'"]+)['"]/g
  let match
  while ((match = pattern.exec(source)) !== null) {
    imports.push(match[1] ?? match[2] ?? match[3])
  }
  return imports
}

function resolveImport(fromFile, specifier) {
  if (!specifier.startsWith('.')) {
    return specifier
  }
  return path.posix.normalize(path.posix.join(path.posix.dirname(fromFile), specifier))
}

function isForbiddenTarget(target, forbiddenRule) {
  if (forbiddenRule.endsWith('/')) {
    return target === forbiddenRule.slice(0, -1) || target.startsWith(forbiddenRule)
  }
  return target === forbiddenRule || target.startsWith(`${forbiddenRule}/`)
}

export function findViolations(sources) {
  const violations = []
  for (const [file, content] of Object.entries(sources)) {
    if (file.endsWith('.test.js') || file.endsWith('.test.jsx')) {
      continue
    }
    const rule = LAYER_FORBIDDEN.find((item) => item.match(file))
    const forbidden = rule ? rule.forbidden : []
    for (const specifier of extractImports(content)) {
      const target = resolveImport(file, specifier)
      if (
        target.endsWith('.test.js') ||
        target.endsWith('.test.jsx') ||
        forbidden.some((forbiddenRule) => isForbiddenTarget(target, forbiddenRule))
      ) {
        violations.push(`${file} imports ${target}`)
      }
    }
  }
  return violations
}

function loadSrcSources(dir = SRC_DIR, prefix = '') {
  const sources = {}
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const relPath = prefix ? `${prefix}/${entry.name}` : entry.name
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      Object.assign(sources, loadSrcSources(fullPath, relPath))
    } else if (entry.isFile() && (entry.name.endsWith('.js') || entry.name.endsWith('.jsx'))) {
      sources[relPath] = readFileSync(fullPath, 'utf-8')
    }
  }
  return sources
}

describe('frontend architecture guard', () => {
  test('guard flags presentation importing infrastructure', () => {
    expect(
      findViolations({
        'presentation/components/X.jsx': "import { a } from '../../infrastructure/api/marketGateway.js'",
      }),
    ).toEqual(['presentation/components/X.jsx imports infrastructure/api/marketGateway.js'])
  })

  test('guard flags side-effect import of infrastructure', () => {
    expect(
      findViolations({
        'presentation/components/X.jsx': "import '../../infrastructure/api/marketGateway.js'",
      }),
    ).toEqual(['presentation/components/X.jsx imports infrastructure/api/marketGateway.js'])
  })

  test('guard flags domain importing react-dom/client', () => {
    expect(
      findViolations({
        'domain/x.js': "import { createRoot } from 'react-dom/client'",
      }),
    ).toEqual(['domain/x.js imports react-dom/client'])
  })

  test('src layers depend inward', () => {
    expect(findViolations(loadSrcSources())).toEqual([])
  })

  test('only infrastructure calls fetch', () => {
    const offenders = Object.entries(loadSrcSources())
      .filter(
        ([file, content]) =>
          !file.startsWith('infrastructure/') &&
          !file.endsWith('.test.js') &&
          !file.endsWith('.test.jsx') &&
          /\bfetch\s*\(/.test(content),
      )
      .map(([file]) => file)
    expect(offenders).toEqual([])
  })
})

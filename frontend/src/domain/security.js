const CANONICAL_RE = /^([0-9A-Z]{1,10})\.(SH|SZ|BJ)$/
const LEGACY_RE = /^(0|1)\.([0-9A-Za-z]{1,10})$/

export function normalizeSecid(value) {
  if (typeof value !== 'string') return null
  const cleaned = value.trim()
  const upper = cleaned.toUpperCase()
  if (CANONICAL_RE.test(upper)) {
    return upper
  }
  const match = LEGACY_RE.exec(cleaned)
  if (match) {
    const [, market, rawCode] = match
    const code = rawCode.toUpperCase()
    if (market === '1') {
      return `${code}.SH`
    }
    if (code.startsWith('4') || code.startsWith('8') || code.startsWith('9')) {
      return `${code}.BJ`
    }
    return `${code}.SZ`
  }
  return null
}

export function exchangeOf(secid) {
  const normalized = normalizeSecid(secid)
  if (!normalized) return null
  const match = CANONICAL_RE.exec(normalized)
  return match ? match[2] : null
}

export function codeOf(secid) {
  const normalized = normalizeSecid(secid)
  if (!normalized) return ''
  const match = CANONICAL_RE.exec(normalized)
  return match ? match[1] : ''
}

function zonedParts(date, timeZone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(date)
  return Object.fromEntries(
    parts.filter((part) => part.type !== 'literal').map((part) => [part.type, Number(part.value)]),
  )
}

export function isMarketOpen(secid, value = new Date()) {
  const date = value instanceof Date ? value : new Date(value)
  const market = secid.split('.')[0]
  const timeZone = ['105', '106', '107'].includes(market)
    ? 'America/New_York'
    : market === '116' ? 'Asia/Hong_Kong' : 'Asia/Shanghai'
  const local = zonedParts(date, timeZone)
  const weekday = new Date(Date.UTC(local.year, local.month - 1, local.day)).getUTCDay()
  if (weekday === 0 || weekday === 6) return false

  const minutes = local.hour * 60 + local.minute
  if (['105', '106', '107'].includes(market)) {
    return minutes >= 570 && minutes < 960
  }
  if (market === '116') {
    return (minutes >= 570 && minutes < 720) || (minutes >= 780 && minutes < 960)
  }
  return (minutes >= 570 && minutes < 690) || (minutes >= 780 && minutes < 900)
}

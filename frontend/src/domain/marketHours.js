// 各市场交易时段（当地时间，自零点起的分钟数）
const US_MARKETS = ['105', '106', '107']
const SESSIONS = {
  us: { timeZone: 'America/New_York', sessions: [[570, 960]] },
  hk: { timeZone: 'Asia/Hong_Kong', sessions: [[570, 720], [780, 960]] },
  cn: { timeZone: 'Asia/Shanghai', sessions: [[570, 690], [780, 900]] },
}

function getMarketSession(secid) {
  const market = secid.split('.')[0]
  if (US_MARKETS.includes(market)) return SESSIONS.us
  return market === '116' ? SESSIONS.hk : SESSIONS.cn
}

function zonedParts(date, timeZone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date)
  return Object.fromEntries(
    parts.filter((part) => part.type !== 'literal').map((part) => [part.type, Number(part.value)]),
  )
}

export function isMarketOpen(secid, value = new Date()) {
  const date = value instanceof Date ? value : new Date(value)
  const { timeZone, sessions } = getMarketSession(secid)
  const local = zonedParts(date, timeZone)
  const weekday = new Date(Date.UTC(local.year, local.month - 1, local.day)).getUTCDay()
  if (weekday === 0 || weekday === 6) return false

  const minutes = local.hour * 60 + local.minute
  return sessions.some(([start, end]) => minutes >= start && minutes < end)
}

export function getTradingDuration(secid) {
  return getMarketSession(secid).sessions.reduce((total, [start, end]) => total + end - start, 0)
}

// 休市在交易分钟轴上的位置（如 A 股午休位于第 120 分钟）
export function getSessionBreaks(secid) {
  const { sessions } = getMarketSession(secid)
  let elapsed = 0
  return sessions.slice(0, -1).map(([start, end]) => {
    elapsed += end - start
    return elapsed
  })
}

// 把时间映射到去掉休市后的交易分钟；休市和盘前盘后的点贴到最近的时段边界
export function toTradingMinute(secid, value) {
  const { timeZone, sessions } = getMarketSession(secid)
  const local = zonedParts(new Date(value), timeZone)
  const minutes = local.hour * 60 + local.minute + local.second / 60
  let elapsed = 0
  for (const [start, end] of sessions) {
    if (minutes < end) return elapsed + Math.max(0, minutes - start)
    elapsed += end - start
  }
  return elapsed
}

// 交易分钟换回当地时钟；恰好落在休市边界时显示上午收盘时间
export function tradingMinuteToClock(secid, tradingMinute) {
  const { sessions } = getMarketSession(secid)
  let remaining = tradingMinute
  let clock = sessions[0][0]
  for (const [start, end] of sessions) {
    clock = start + remaining
    if (remaining <= end - start) break
    remaining -= end - start
  }
  const pad = (number) => String(number).padStart(2, '0')
  const whole = Math.floor(clock)
  return `${pad(Math.floor(whole / 60))}:${pad(whole % 60)}`
}

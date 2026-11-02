import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

function formatTime(value, includeSeconds = false) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return includeSeconds ? `${time}:${pad(date.getSeconds())}` : time
}

function zonedParts(value, timeZone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(value))
  return Object.fromEntries(
    parts.filter((part) => part.type !== 'literal').map((part) => [part.type, Number(part.value)]),
  )
}

function zonedTimeToUtc(referenceTime, timeZone, hour, minute) {
  const local = zonedParts(referenceTime, timeZone)
  const guess = Date.UTC(local.year, local.month - 1, local.day, hour, minute)
  const actual = zonedParts(guess, timeZone)
  const offset = Date.UTC(
    actual.year, actual.month - 1, actual.day, actual.hour, actual.minute, actual.second,
  ) - guess
  return guess - offset
}

export function getSessionBounds(secid, referenceTime) {
  const market = secid.split('.')[0]
  const timeZone = ['105', '106', '107'].includes(market)
    ? 'America/New_York'
    : market === '116' ? 'Asia/Hong_Kong' : 'Asia/Shanghai'
  const closeHour = ['105', '106', '107', '116'].includes(market) ? 16 : 15
  return {
    min: zonedTimeToUtc(referenceTime, timeZone, 9, 30),
    max: zonedTimeToUtc(referenceTime, timeZone, closeHour, 0),
  }
}

function buildOption(points, secid, previousClose) {
  const latest = points.at(-1)
  const session = getSessionBounds(secid, latest?.time || new Date())
  const prices = points.map((point) => point.price)
  const highest = Math.max(...prices, previousClose)
  const lowest = Math.min(...prices, previousClose)
  const padding = Math.max((highest - lowest) * 0.08, previousClose * 0.0005)
  const minPrice = Math.max(0, lowest - padding)
  const maxPrice = highest + padding
  const percentLabel = (value) => {
    const percent = (value - previousClose) / previousClose * 100
    return `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`
  }
  return {
    animationDuration: 220,
    backgroundColor: 'transparent',
    grid: { left: 0, right: 0, top: 22, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(246,245,241,.98)',
      borderColor: '#d8d8d1',
      textStyle: { color: '#2d302d', fontSize: 12 },
      formatter: (params) => {
        const point = params[0]
        return point ? `${formatTime(point.value[0], true)}<br/><b>${Number(point.value[1]).toFixed(3)}</b>` : ''
      },
    },
    xAxis: {
      type: 'time',
      min: session.min,
      max: session.max,
      axisLine: { lineStyle: { color: '#d8d8d1' } },
      axisTick: { show: false },
      axisLabel: { color: '#858a82', fontSize: 11, formatter: (value) => formatTime(value), hideOverlap: true },
      splitLine: { show: false },
    },
    yAxis: [
      {
        type: 'value',
        min: minPrice,
        max: maxPrice,
        position: 'left',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#737b71', fontSize: 11, inside: true, margin: 8, formatter: (value) => value.toFixed(3) },
        splitLine: { lineStyle: { color: '#e1e0da', type: 'dashed' } },
      },
      {
        type: 'value',
        min: minPrice,
        max: maxPrice,
        position: 'right',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#737b71', fontSize: 11, inside: true, margin: 8, formatter: percentLabel },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '实时价格',
        type: 'line',
        data: points.map((point) => [point.time, point.price]),
        showSymbol: false,
        smooth: 0.12,
        lineStyle: { color: '#327d6b', width: 2.2 },
        itemStyle: { color: '#327d6b' },
        areaStyle: { color: 'rgba(50,125,107,.12)' },
        markPoint: {
          symbol: 'circle',
          symbolSize: 8,
          label: { show: false },
          itemStyle: { color: '#327d6b', borderColor: '#d8f0e5', borderWidth: 3 },
          data: latest ? [{ coord: [latest.time, latest.price] }] : [],
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#b48a42', type: 'dashed', width: 1.2 },
          label: { show: false },
          data: [{ yAxis: previousClose }],
        },
      },
    ],
  }
}

export default function RealtimeChart({ points, secid, previousClose }) {
  const containerRef = useRef(null)
  const chartRef = useRef(null)

  useEffect(() => {
    const chart = echarts.init(containerRef.current, null, { renderer: 'canvas' })
    chartRef.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(containerRef.current)
    return () => {
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(buildOption(points, secid, previousClose), { notMerge: true })
  }, [points, secid, previousClose])

  return <div className="realtime-line-chart" ref={containerRef} role="img" aria-label="当日实时价格走势图" />
}

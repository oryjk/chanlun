import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { formatPrice } from '../../domain/market.ts'
import { getSessionBreaks, getTradingDuration, toTradingMinute, tradingMinuteToClock } from '../../domain/marketHours.ts'
import type { QuotePoint, ResolvedTheme } from '../../domain/types.ts'

function formatTime(value: string, includeSeconds = false): string {
  const date = new Date(value)
  const pad = (number: number) => String(number).padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return includeSeconds ? `${time}:${pad(date.getSeconds())}` : time
}

function buildOption(
  points: QuotePoint[],
  secid: string,
  previousClose: number,
  decimals: number,
  theme: ResolvedTheme = 'light',
): echarts.EChartsOption {
  const dark = theme === 'dark'
  const lineColor = dark ? '#5e89c0' : '#2e4a6e'
  const areaColor = dark ? 'rgba(94,137,192,0.16)' : 'rgba(46,74,110,.1)'
  const axisLine = dark ? '#2f3336' : '#e3e6ea'
  const axisText = dark ? '#71767b' : '#8b919a'
  const splitLine = dark ? '#1c1f24' : '#eef0f2'
  const tooltipBg = dark ? 'rgba(22,24,28,.98)' : 'rgba(255,255,255,.98)'
  const tooltipText = dark ? '#e7e9ea' : '#1a1d21'
  const latest = points[points.length - 1]
  // 横轴为去掉休市后的交易分钟，午休前后两段首尾相接
  const toX = (point: QuotePoint) => toTradingMinute(secid, point.time)
  const prices = points.map((point) => point.price)
  const highest = Math.max(...prices, previousClose)
  const lowest = Math.min(...prices, previousClose)
  const padding = Math.max((highest - lowest) * 0.08, previousClose * 0.0005)
  const minPrice = Math.max(0, lowest - padding)
  const maxPrice = highest + padding
  const percentLabel = (value: number) => {
    const percent = ((value - previousClose) / previousClose) * 100
    if (Math.abs(percent) < 0.005) return '0.00%'
    return `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`
  }
  return {
    animationDuration: 220,
    backgroundColor: 'transparent',
    grid: { left: 0, right: 0, top: 22, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: tooltipBg,
      borderColor: axisLine,
      textStyle: { color: tooltipText, fontSize: 12 },
      formatter: (rawParams: unknown) => {
        const params = Array.isArray(rawParams) ? rawParams : [rawParams]
        const point = params[0] as { value?: [number, number, string] } | undefined
        return point?.value ? `${formatTime(point.value[2], true)}<br/><b>${formatPrice(point.value[1], decimals)}</b>` : ''
      },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: getTradingDuration(secid),
      interval: 30,
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => tradingMinuteToClock(secid, value),
        hideOverlap: true,
        alignMinLabel: 'left',
        alignMaxLabel: 'right',
      },
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
        axisLabel: { color: axisText, fontSize: 11, inside: true, margin: 8, formatter: (value: number) => formatPrice(value, decimals) },
        splitLine: { lineStyle: { color: splitLine, type: 'dashed' } },
      },
      {
        type: 'value',
        min: minPrice,
        max: maxPrice,
        position: 'right',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: axisText, fontSize: 11, inside: true, margin: 8, formatter: percentLabel },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '实时价格',
        type: 'line',
        data: points.map((point) => [toX(point), point.price, point.time]),
        showSymbol: false,
        smooth: 0.12,
        lineStyle: { color: lineColor, width: 2.2 },
        itemStyle: { color: lineColor },
        areaStyle: { color: areaColor },
        markPoint: {
          symbol: 'circle',
          symbolSize: 8,
          label: { show: false },
          itemStyle: { color: lineColor, borderColor: dark ? '#202c3d' : '#e6ebf2', borderWidth: 3 },
          data: latest ? [{ name: '最新价', coord: [toX(latest), latest.price] }] : [],
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: dark ? '#d4a656' : '#b48a42', type: 'dashed', width: 1.2 },
          label: { show: false },
          data: [
            { yAxis: previousClose },
            ...getSessionBreaks(secid).map((position) => ({
              xAxis: position,
              lineStyle: { color: axisText, type: 'solid' as const, width: 1 },
              label: { show: true, position: 'insideEndTop' as const, formatter: '休市', color: axisText, fontSize: 10 },
            })),
          ],
        },
      },
    ],
  }
}

export interface RealtimeChartProps {
  points: QuotePoint[]
  secid: string
  previousClose: number
  decimals: number
  theme?: ResolvedTheme
}

export default function RealtimeChart({
  points,
  secid,
  previousClose,
  decimals,
  theme = 'light',
}: RealtimeChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!containerRef.current) return undefined
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
    chartRef.current?.setOption(buildOption(points, secid, previousClose, decimals, theme), { notMerge: true })
  }, [points, secid, previousClose, decimals, theme])

  return <div className="realtime-line-chart" ref={containerRef} role="img" aria-label="当日实时价格走势图" />
}

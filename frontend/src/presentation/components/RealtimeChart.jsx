import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { getSessionBreaks, getTradingDuration, toTradingMinute, tradingMinuteToClock } from '../../domain/marketHours.js'

function formatTime(value, includeSeconds = false) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return includeSeconds ? `${time}:${pad(date.getSeconds())}` : time
}

function buildOption(points, secid, previousClose) {
  const latest = points.at(-1)
  // 横轴为去掉休市后的交易分钟，午休前后两段首尾相接
  const toX = (point) => toTradingMinute(secid, point.time)
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
        return point ? `${formatTime(point.value[2], true)}<br/><b>${Number(point.value[1]).toFixed(3)}</b>` : ''
      },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: getTradingDuration(secid),
      interval: 30,
      axisLine: { lineStyle: { color: '#d8d8d1' } },
      axisTick: { show: false },
      axisLabel: { color: '#858a82', fontSize: 11, formatter: (value) => tradingMinuteToClock(secid, value), hideOverlap: true, alignMinLabel: 'left', alignMaxLabel: 'right' },
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
        data: points.map((point) => [toX(point), point.price, point.time]),
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
          data: latest ? [{ coord: [toX(latest), latest.price] }] : [],
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#b48a42', type: 'dashed', width: 1.2 },
          label: { show: false },
          data: [
            { yAxis: previousClose },
            ...getSessionBreaks(secid).map((position) => ({
              xAxis: position,
              lineStyle: { color: '#9aa49b', type: 'solid', width: 1 },
              label: { show: true, position: 'insideEndTop', formatter: '休市', color: '#858a82', fontSize: 10 },
            })),
          ],
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

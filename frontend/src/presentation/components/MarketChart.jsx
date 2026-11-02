import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

const UP = '#d94f5e'
const DOWN = '#15936e'
const GOLD = '#b48a42'

function axisTime(value, timeframe) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  if (timeframe === '1d') return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fullTime(value) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function compactVolume(value) {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (value >= 10000) return `${Math.round(value / 10000)}万`
  return value
}

function buildOption(candles, analysis, timeframe, layers) {
  const times = candles.map((item) => item.time)
  const confirmedPenPoints = Array(candles.length).fill(null)
  const candidatePenPoints = Array(candles.length).fill(null)
  const confirmedSegmentPoints = Array(candles.length).fill(null)
  const candidateSegmentPoints = Array(candles.length).fill(null)
  analysis.pens.forEach((pen) => {
    const points = pen.confirmed ? confirmedPenPoints : candidatePenPoints
    points[pen.start_index] = pen.start_price
    points[pen.end_index] = pen.end_price
  })
  analysis.segments.forEach((segment) => {
    const points = segment.confirmed ? confirmedSegmentPoints : candidateSegmentPoints
    points[segment.start_index] = segment.start_price
    points[segment.end_index] = segment.end_price
  })

  const tooltipFormatter = (params) => {
    const candleParam = params.find((item) => item.seriesName === 'K线')
    if (!candleParam) return ''
    const candle = candles[candleParam.dataIndex]
    const rising = candle.close >= candle.open
    const color = rising ? UP : DOWN
    const rows = [
      ['开盘', candle.open.toFixed(3)],
      ['最高', candle.high.toFixed(3)],
      ['最低', candle.low.toFixed(3)],
      ['收盘', candle.close.toFixed(3)],
      ['成交量', compactVolume(candle.volume)],
    ]
    return `<div class="chart-tooltip"><div class="tooltip-time">${fullTime(candle.time)}</div>${rows.map(([label, value]) => `<div class="tooltip-row"><span>${label}</span><b style="color:${label === '收盘' ? color : '#2d302d'}">${value}</b></div>`).join('')}</div>`
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#327d6b', color: '#ffffff', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(246, 245, 241, 0.98)',
      borderColor: '#d8d8d1',
      borderWidth: 1,
      padding: [12, 14],
      textStyle: { color: '#2d302d', fontSize: 12 },
      axisPointer: { type: 'cross', lineStyle: { color: '#9aa49b', type: 'dashed' }, crossStyle: { color: '#9aa49b' } },
      formatter: tooltipFormatter,
    },
    grid: [
      { left: 16, right: 72, top: 24, height: '58%' },
      { left: 16, right: 72, top: '72%', height: '16%' },
    ],
    xAxis: [
      {
        type: 'category', data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#d8d8d1' } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 1, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#d8d8d1' } }, axisTick: { show: false },
        axisLabel: { color: '#858a82', fontSize: 11, margin: 11, formatter: (value) => axisTime(value, timeframe), hideOverlap: true },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true, position: 'right', axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#737b71', fontSize: 11, formatter: (value) => value.toFixed(3), margin: 12 },
        splitLine: { lineStyle: { color: '#e1e0da', type: 'dashed' } },
      },
      {
        scale: true, gridIndex: 1, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#858a82', fontSize: 11, formatter: compactVolume, margin: 12 },
        splitLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 52, end: 100, zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: false },
      {
        type: 'slider', xAxisIndex: [0, 1], start: 52, end: 100, bottom: 5, height: 15,
        showDetail: false, borderColor: 'transparent', backgroundColor: '#e8e7e2', fillerColor: 'rgba(167,222,207,0.35)',
        handleStyle: { color: '#a7decf', borderColor: '#327d6b' }, moveHandleStyle: { color: '#82bfae' },
        emphasis: { moveHandleStyle: { color: '#327d6b' } }, dataBackground: { lineStyle: { color: '#c5c9c2' }, areaStyle: { color: '#ecebe6' } },
        selectedDataBackground: { lineStyle: { color: '#327d6b' }, areaStyle: { color: '#d8e9e1' } },
      },
    ],
    series: [
      {
        name: 'K线', type: 'candlestick', data: candles.map((item) => [item.open, item.close, item.low, item.high]),
        barMaxWidth: 12,
        itemStyle: { color: UP, color0: DOWN, borderColor: UP, borderColor0: DOWN, borderWidth: 1 },
      },
      {
        name: '已确认笔', type: 'line', data: layers.pens ? confirmedPenPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 5,
        lineStyle: { color: GOLD, width: 2 },
      },
      {
        name: '候选笔', type: 'line', data: layers.pens ? candidatePenPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 5,
        lineStyle: { color: GOLD, width: 1.8, type: 'dashed' },
      },
      {
        name: '已确认线段', type: 'line', data: layers.segments ? confirmedSegmentPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 4,
        lineStyle: { color: '#327d6b', width: 2.4 },
      },
      {
        name: '候选线段', type: 'line', data: layers.segments ? candidateSegmentPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 4,
        lineStyle: { color: '#62a792', width: 2, type: 'dashed' },
      },
      {
        name: '分型', type: 'scatter', data: layers.fractals ? analysis.fractals.map((item) => [item.time, item.price, item.type]) : [],
        symbol: 'triangle', symbolSize: 8, symbolRotate: (value) => value[2] === 'bottom' ? 180 : 0,
        itemStyle: { color: (params) => params.value[2] === 'top' ? '#b48a42' : '#7294aa', borderColor: '#f5f4f0', borderWidth: 1 },
        silent: true, z: 6,
      },
      {
        name: '笔中枢', type: 'line', data: [], silent: true, z: 3,
        markArea: {
          silent: true,
          itemStyle: { color: 'rgba(114, 138, 175, 0.12)', borderColor: 'rgba(114, 138, 175, 0.45)', borderWidth: 1 },
          label: { show: true, color: '#657b9c', fontSize: 11, position: 'insideTopLeft', padding: 5 },
          data: layers.zhongshu ? analysis.zhongshu.map((zone) => [
            { name: `笔中枢 ${zone.id}${zone.confirmed ? '' : ' · 候选'}`, xAxis: zone.start_time, yAxis: zone.low },
            { xAxis: zone.end_time, yAxis: zone.high },
          ]) : [],
        },
      },
      {
        name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, barMaxWidth: 10,
        data: candles.map((item) => ({
          value: item.volume,
          itemStyle: { color: item.close >= item.open ? 'rgba(217,79,94,0.48)' : 'rgba(21,147,110,0.48)' },
        })),
      },
    ],
  }
}

export default function MarketChart({ candles, analysis, timeframe, layers }) {
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
    if (!chartRef.current) return
    chartRef.current.setOption(buildOption(candles, analysis, timeframe, layers), { notMerge: true })
  }, [candles, analysis, timeframe, layers])

  return <div className="market-chart" ref={containerRef} role="img" aria-label={`${timeframe} 周期 K 线、成交量与缠论结构图`} />
}

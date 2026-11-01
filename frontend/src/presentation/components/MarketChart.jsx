import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

const UP = '#f06470'
const DOWN = '#23b898'
const GOLD = '#e3b668'

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
  const penPoints = Array(candles.length).fill(null)
  analysis.pens.forEach((pen) => {
    penPoints[pen.start_index] = pen.start_price
    penPoints[pen.end_index] = pen.end_price
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
    return `<div class="chart-tooltip"><div class="tooltip-time">${fullTime(candle.time)}</div>${rows.map(([label, value]) => `<div class="tooltip-row"><span>${label}</span><b style="color:${label === '收盘' ? color : '#e9edf5'}">${value}</b></div>`).join('')}</div>`
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#303744', color: '#e7ebf2', fontSize: 10 },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(20, 24, 32, 0.96)',
      borderColor: '#343b48',
      borderWidth: 1,
      padding: [10, 12],
      textStyle: { color: '#e9edf5', fontSize: 12 },
      axisPointer: { type: 'cross', lineStyle: { color: '#606a79', type: 'dashed' }, crossStyle: { color: '#606a79' } },
      formatter: tooltipFormatter,
    },
    grid: [
      { left: 12, right: 64, top: 24, height: '57%' },
      { left: 12, right: 64, top: '70%', height: '18%' },
    ],
    xAxis: [
      {
        type: 'category', data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#252b35' } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 1, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#252b35' } }, axisTick: { show: false },
        axisLabel: { color: '#697384', fontSize: 10, margin: 10, formatter: (value) => axisTime(value, timeframe), hideOverlap: true },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true, position: 'right', axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#7f8999', fontSize: 10, formatter: (value) => value.toFixed(3), margin: 10 },
        splitLine: { lineStyle: { color: '#20252e', type: 'dashed' } },
      },
      {
        scale: true, gridIndex: 1, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#697384', fontSize: 10, formatter: compactVolume, margin: 10 },
        splitLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 52, end: 100, zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: false },
      {
        type: 'slider', xAxisIndex: [0, 1], start: 52, end: 100, bottom: 5, height: 14,
        showDetail: false, borderColor: 'transparent', backgroundColor: '#11161e', fillerColor: 'rgba(227,182,104,0.12)',
        handleStyle: { color: '#8c744d', borderColor: '#b69966' }, moveHandleStyle: { color: '#665638' },
        emphasis: { moveHandleStyle: { color: '#b69966' } }, dataBackground: { lineStyle: { color: '#343c49' }, areaStyle: { color: '#1e2630' } },
        selectedDataBackground: { lineStyle: { color: '#8c744d' }, areaStyle: { color: '#332d24' } },
      },
    ],
    series: [
      {
        name: 'K线', type: 'candlestick', data: candles.map((item) => [item.open, item.close, item.low, item.high]),
        barMaxWidth: 12,
        itemStyle: { color: UP, color0: DOWN, borderColor: UP, borderColor0: DOWN, borderWidth: 1 },
      },
      {
        name: '笔', type: 'line', data: layers.pens ? penPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 5,
        lineStyle: { color: GOLD, width: 1.8, shadowColor: 'rgba(227,182,104,0.25)', shadowBlur: 5 },
      },
      {
        name: '分型', type: 'scatter', data: layers.fractals ? analysis.fractals.map((item) => [item.time, item.price, item.type]) : [],
        symbol: 'triangle', symbolSize: 8, symbolRotate: (value) => value[2] === 'bottom' ? 180 : 0,
        itemStyle: { color: (params) => params.value[2] === 'top' ? '#f1cf91' : '#8da9c7', borderColor: '#10141b', borderWidth: 1 },
        silent: true, z: 6,
      },
      {
        name: '中枢', type: 'line', data: [], silent: true, z: 3,
        markArea: {
          silent: true,
          itemStyle: { color: 'rgba(100, 126, 171, 0.12)', borderColor: 'rgba(119, 147, 196, 0.55)', borderWidth: 1 },
          label: { show: true, color: '#90a5c5', fontSize: 10, position: 'insideTopLeft', padding: 4 },
          data: layers.zhongshu ? analysis.zhongshu.map((zone) => [
            { name: `中枢 ${zone.id}`, xAxis: zone.start_time, yAxis: zone.low },
            { xAxis: zone.end_time, yAxis: zone.high },
          ]) : [],
        },
      },
      {
        name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, barMaxWidth: 10,
        data: candles.map((item) => ({
          value: item.volume,
          itemStyle: { color: item.close >= item.open ? 'rgba(240,100,112,0.55)' : 'rgba(35,184,152,0.55)' },
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

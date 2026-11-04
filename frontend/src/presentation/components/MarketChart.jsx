import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { pointLabel, pointShortLabel } from '../../domain/signals.js'
import { formatPrice } from '../../domain/market.js'

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

function buildOption(candles, analysis, macd, signals, timeframe, layers, decimals) {
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
    const macdPoint = macd[candleParam.dataIndex]
    const pointRows = signals.points
      .filter((point) => point.candle_index === candleParam.dataIndex)
      .map((point) => `<div class="tooltip-row"><span>${pointLabel(point)}${point.confirmed ? '' : '（候选）'}</span><b style="color:${point.side === 'buy' ? UP : DOWN}">${formatPrice(point.price, decimals)}</b></div>`)
      .join('')
    const rows = [
      ['开盘', formatPrice(candle.open, decimals)],
      ['最高', formatPrice(candle.high, decimals)],
      ['最低', formatPrice(candle.low, decimals)],
      ['收盘', formatPrice(candle.close, decimals)],
      ['成交量', compactVolume(candle.volume)],
      ...(macdPoint ? [['MACD', macdPoint.histogram.toFixed(3)]] : []),
    ]
    return `<div class="chart-tooltip"><div class="tooltip-time">${fullTime(candle.time)}</div>${rows.map(([label, value]) => `<div class="tooltip-row"><span>${label}</span><b style="color:${label === '收盘' ? color : '#1a1d21'}">${value}</b></div>`).join('')}${pointRows}</div>`
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#2e4a6e', color: '#ffffff', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      borderColor: '#e3e6ea',
      borderWidth: 1,
      padding: [12, 14],
      textStyle: { color: '#1a1d21', fontSize: 12 },
      axisPointer: { type: 'cross', lineStyle: { color: '#9aa1aa', type: 'dashed' }, crossStyle: { color: '#9aa1aa' } },
      formatter: tooltipFormatter,
    },
    grid: [
      { left: 16, right: 72, top: 24, height: '52%' },
      { left: 16, right: 72, top: '62%', height: '10%' },
      { left: 16, right: 72, top: '76%', height: '13%' },
    ],
    xAxis: [
      {
        type: 'category', data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#e3e6ea' } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 1, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#e3e6ea' } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 2, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: '#e3e6ea' } }, axisTick: { show: false },
        axisLabel: { color: '#8b919a', fontSize: 11, margin: 11, formatter: (value) => axisTime(value, timeframe), hideOverlap: true },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true, position: 'right', axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#8b919a', fontSize: 11, formatter: (value) => formatPrice(value, decimals), margin: 12 },
        splitLine: { lineStyle: { color: '#eef0f2', type: 'dashed' } },
      },
      {
        scale: true, gridIndex: 1, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#8b919a', fontSize: 11, formatter: compactVolume, margin: 12 },
        splitLine: { show: false },
      },
      {
        scale: true, gridIndex: 2, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: '#8b919a', fontSize: 11, formatter: (value) => value.toFixed(2), margin: 12 },
        splitLine: { lineStyle: { color: '#eef0f2', type: 'dashed' } },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2], start: 52, end: 100, zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: false },
      {
        type: 'slider', xAxisIndex: [0, 1, 2], start: 52, end: 100, bottom: 5, height: 15,
        showDetail: false, borderColor: 'transparent', backgroundColor: '#f4f5f7', fillerColor: 'rgba(46,74,110,0.1)',
        handleStyle: { color: '#ffffff', borderColor: '#2e4a6e' }, moveHandleStyle: { color: '#9aabc2' },
        emphasis: { moveHandleStyle: { color: '#2e4a6e' } }, dataBackground: { lineStyle: { color: '#d2d6dc' }, areaStyle: { color: '#eef0f2' } },
        selectedDataBackground: { lineStyle: { color: '#2e4a6e' }, areaStyle: { color: '#e6ebf2' } },
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
        lineStyle: { color: '#2e4a6e', width: 2.4 },
      },
      {
        name: '候选线段', type: 'line', data: layers.segments ? candidateSegmentPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 4,
        lineStyle: { color: '#7189aa', width: 2, type: 'dashed' },
      },
      {
        name: '分型', type: 'scatter', data: layers.fractals ? analysis.fractals.map((item) => [item.time, item.price, item.type]) : [],
        symbol: 'triangle', symbolSize: 8, symbolRotate: (value) => value[2] === 'bottom' ? 180 : 0,
        itemStyle: { color: (params) => params.value[2] === 'top' ? '#b48a42' : '#7294aa', borderColor: '#ffffff', borderWidth: 1 },
        silent: true, z: 6,
      },
      {
        name: '笔中枢', type: 'line', data: [], silent: true, z: 3,
        markArea: {
          silent: true,
          itemStyle: { color: 'rgba(125, 104, 168, 0.1)', borderColor: 'rgba(125, 104, 168, 0.55)', borderWidth: 1 },
          label: { show: false },
          data: layers.zhongshu ? analysis.zhongshu.map((zone) => [
            { xAxis: zone.start_time, yAxis: zone.low },
            { xAxis: zone.end_time, yAxis: zone.high },
          ]) : [],
        },
      },
      {
        // 中枢标签单独成层，避免被 K 线和笔遮挡
        name: '笔中枢标签', type: 'scatter', silent: true, z: 9, symbolSize: 1, itemStyle: { color: 'transparent' },
        data: layers.zhongshu ? analysis.zhongshu.map((zone) => ({
          value: [zone.end_time, zone.high],
          label: { formatter: `中枢${zone.id}${zone.confirmed ? '' : ' 候选'}` },
        })) : [],
        label: {
          show: true, position: 'top', align: 'right', distance: 3, color: '#5f4d86', fontSize: 10, fontWeight: 600,
          backgroundColor: 'rgba(255, 255, 255, 0.92)', borderColor: 'rgba(125, 104, 168, 0.35)', borderWidth: 1,
          borderRadius: 3, padding: [2, 4],
        },
      },
      {
        name: '买卖点', type: 'scatter', z: 8,
        data: layers.points ? signals.points.map((point) => ({
          value: [point.time, point.price],
          symbolRotate: point.side === 'buy' ? 180 : 0,
          symbolOffset: [0, point.side === 'buy' ? 15 : -15],
          label: { formatter: pointShortLabel(point), offset: [0, point.side === 'buy' ? 3 : -3] },
          itemStyle: { color: point.side === 'buy' ? UP : DOWN, opacity: point.confirmed ? 0.92 : 0.45 },
        })) : [],
        symbol: 'pin', symbolSize: 30, silent: true,
        label: { show: true, color: '#ffffff', fontSize: 10, fontWeight: 700 },
      },
      {
        name: 'MACD', type: 'bar', xAxisIndex: 2, yAxisIndex: 2, barMaxWidth: 6,
        data: macd.map((point) => ({
          value: point.histogram,
          itemStyle: { color: point.histogram >= 0 ? 'rgba(217,79,94,0.6)' : 'rgba(21,147,110,0.6)' },
        })),
      },
      {
        name: 'DIF', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: macd.map((point) => point.dif),
        showSymbol: false, silent: true, lineStyle: { color: '#1a1d21', width: 1 },
      },
      {
        name: 'DEA', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: macd.map((point) => point.dea),
        showSymbol: false, silent: true, lineStyle: { color: GOLD, width: 1 },
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

export default function MarketChart({ candles, analysis, macd, signals, timeframe, layers, decimals }) {
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
    chartRef.current.setOption(buildOption(candles, analysis, macd, signals, timeframe, layers, decimals), { notMerge: true })
  }, [candles, analysis, macd, signals, timeframe, layers, decimals])

  return <div className="market-chart" ref={containerRef} role="img" aria-label={`${timeframe} 周期 K 线、成交量、MACD 与缠论结构图`} />
}

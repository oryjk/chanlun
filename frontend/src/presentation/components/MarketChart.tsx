import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { formatPrice, getCandleChange } from '../../domain/market.ts'
import { pointLabel, pointShortLabel } from '../../domain/signals.ts'
import type {
  Candle,
  ChanAnalysis,
  ChanSignals,
  MacdPoint,
  ResolvedTheme,
} from '../../domain/types.ts'

interface ChartPalette {
  up: string
  down: string
  gold: string
  axisLine: string
  axisText: string
  splitLine: string
  tooltipBg: string
  tooltipBorder: string
  tooltipText: string
  accent: string
  accentSoft: string
  segment: string
  segmentCandidate: string
  dif: string
  fractalBorder: string
  zhongshuText: string
  zhongshuBg: string
  sliderBg: string
  sliderHandleBg: string
  sliderArea: string
  sliderSelectedArea: string
  macdUp: string
  macdDown: string
  volUp: string
  volDown: string
}

const LIGHT_PALETTE: ChartPalette = {
  up: '#d94f5e',
  down: '#15936e',
  gold: '#b48a42',
  axisLine: '#e3e6ea',
  axisText: '#8b919a',
  splitLine: '#eef0f2',
  tooltipBg: 'rgba(255, 255, 255, 0.98)',
  tooltipBorder: '#e3e6ea',
  tooltipText: '#1a1d21',
  accent: '#2e4a6e',
  accentSoft: 'rgba(46,74,110,0.1)',
  segment: '#2e4a6e',
  segmentCandidate: '#7189aa',
  dif: '#1a1d21',
  fractalBorder: '#ffffff',
  zhongshuText: '#5f4d86',
  zhongshuBg: 'rgba(255, 255, 255, 0.92)',
  sliderBg: '#f4f5f7',
  sliderHandleBg: '#ffffff',
  sliderArea: '#eef0f2',
  sliderSelectedArea: '#e6ebf2',
  macdUp: 'rgba(217,79,94,0.6)',
  macdDown: 'rgba(21,147,110,0.6)',
  volUp: 'rgba(217,79,94,0.48)',
  volDown: 'rgba(21,147,110,0.48)',
}

const DARK_PALETTE: ChartPalette = {
  up: '#ff6b6b',
  down: '#30d158',
  gold: '#d4a656',
  axisLine: '#2f3336',
  axisText: '#71767b',
  splitLine: '#1c1f24',
  tooltipBg: 'rgba(22, 24, 28, 0.98)',
  tooltipBorder: '#2f3336',
  tooltipText: '#e7e9ea',
  accent: '#5e89c0',
  accentSoft: 'rgba(94,137,192,0.16)',
  segment: '#78a2d4',
  segmentCandidate: '#5b7aa2',
  dif: '#e7e9ea',
  fractalBorder: '#000000',
  zhongshuText: '#cbb6f7',
  zhongshuBg: 'rgba(22, 24, 28, 0.94)',
  sliderBg: '#16181c',
  sliderHandleBg: '#16181c',
  sliderArea: '#1c1f24',
  sliderSelectedArea: '#202c3d',
  macdUp: 'rgba(255,107,107,0.65)',
  macdDown: 'rgba(48,209,88,0.65)',
  volUp: 'rgba(255,107,107,0.52)',
  volDown: 'rgba(48,209,88,0.52)',
}

export interface ChartLayersState {
  fractals: boolean
  pens: boolean
  segments: boolean
  zhongshu: boolean
  points: boolean
}

function axisTime(value: string, timeframe: string): string {
  const date = new Date(value)
  const pad = (number: number) => String(number).padStart(2, '0')
  if (timeframe === '1d') return `${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fullTime(value: string): string {
  const date = new Date(value)
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function compactVolume(value: number): string {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (value >= 10000) return `${Math.round(value / 10000)}万`
  return String(value)
}

function buildOption(
  candles: Candle[],
  analysis: ChanAnalysis,
  macd: MacdPoint[],
  signals: ChanSignals,
  timeframe: string,
  layers: ChartLayersState,
  decimals: number,
  theme: ResolvedTheme = 'light',
): echarts.EChartsOption {
  const palette = theme === 'dark' ? DARK_PALETTE : LIGHT_PALETTE
  const UP = palette.up
  const DOWN = palette.down
  const GOLD = palette.gold
  const times = candles.map((item) => item.time)
  const confirmedPenPoints: Array<number | null> = Array(candles.length).fill(null)
  const candidatePenPoints: Array<number | null> = Array(candles.length).fill(null)
  const confirmedSegmentPoints: Array<number | null> = Array(candles.length).fill(null)
  const candidateSegmentPoints: Array<number | null> = Array(candles.length).fill(null)
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

  const tooltipFormatter = (rawParams: unknown): string => {
    const params = Array.isArray(rawParams) ? rawParams : [rawParams]
    const candleParam = params.find((item) => (item as { seriesName?: string })?.seriesName === 'K线') as
      | { dataIndex: number }
      | undefined
    if (!candleParam) return ''
    const candle = candles[candleParam.dataIndex]
    const changeInfo = getCandleChange(candles, candleParam.dataIndex, decimals)
    const changeColor = changeInfo
      ? (changeInfo.direction === 'up' ? UP : DOWN)
      : (candle.close >= candle.open ? UP : DOWN)
    const macdPoint = macd[candleParam.dataIndex]
    const pointRows = signals.points
      .filter((point) => point.candle_index === candleParam.dataIndex)
      .map((point) => (
        `<div class="tooltip-row"><span>${pointLabel(point)}${point.confirmed ? '（结构已确认）' : '（候选）'}</span>`
        + `<b style="color:${point.side === 'buy' ? UP : DOWN}">${formatPrice(point.price, decimals)}</b></div>`
        + `<div class="tooltip-row"><span>结构端点</span><b>${fullTime(point.time || '')}</b></div>`
        + '<div class="tooltip-row"><span>首次识别 / 确认时间</span><b>未追溯</b></div>'
      ))
      .join('')
    const rows: Array<[string, string, string?]> = [
      ['开盘', formatPrice(candle.open, decimals)],
      ['最高', formatPrice(candle.high, decimals)],
      ['最低', formatPrice(candle.low, decimals)],
      ['收盘', formatPrice(candle.close, decimals), changeColor],
      ...(changeInfo ? [
        ['涨跌额', changeInfo.formattedChange, changeColor] as [string, string, string],
        ['涨跌幅', changeInfo.formattedPercent, changeColor] as [string, string, string],
      ] : []),
      ['成交量', compactVolume(candle.volume)],
      ...(macdPoint ? [['MACD', macdPoint.histogram.toFixed(3)] as [string, string]] : []),
    ]
    return `<div class="chart-tooltip"><div class="tooltip-time">${fullTime(candle.time)}</div>${rows.map(([label, value, rowColor]) => `<div class="tooltip-row"><span>${label}</span><b style="color:${rowColor || palette.tooltipText}">${value}</b></div>`).join('')}${pointRows}</div>`
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: palette.accent, color: '#ffffff', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      borderWidth: 1,
      padding: [12, 14],
      textStyle: { color: palette.tooltipText, fontSize: 12 },
      axisPointer: { type: 'cross', lineStyle: { color: palette.axisText, type: 'dashed' }, crossStyle: { color: palette.axisText } },
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
        axisLine: { lineStyle: { color: palette.axisLine } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 1, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: palette.axisLine } }, axisTick: { show: false }, axisLabel: { show: false },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
      {
        type: 'category', gridIndex: 2, data: times, boundaryGap: true,
        axisLine: { lineStyle: { color: palette.axisLine } }, axisTick: { show: false },
        axisLabel: { color: palette.axisText, fontSize: 11, margin: 11, formatter: (value: string) => axisTime(value, timeframe), hideOverlap: true },
        splitLine: { show: false }, min: 'dataMin', max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true, position: 'right', axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: palette.axisText, fontSize: 11, formatter: (value: number) => formatPrice(value, decimals), margin: 12 },
        splitLine: { lineStyle: { color: palette.splitLine, type: 'dashed' } },
      },
      {
        scale: true, gridIndex: 1, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: palette.axisText, fontSize: 11, formatter: compactVolume, margin: 12 },
        splitLine: { show: false },
      },
      {
        scale: true, gridIndex: 2, position: 'right', splitNumber: 2,
        axisLine: { show: false }, axisTick: { show: false },
        axisLabel: { color: palette.axisText, fontSize: 11, formatter: (value: number) => value.toFixed(2), margin: 12 },
        splitLine: { lineStyle: { color: palette.splitLine, type: 'dashed' } },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2], start: 52, end: 100, zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: false },
      {
        type: 'slider', xAxisIndex: [0, 1, 2], start: 52, end: 100, bottom: 5, height: 15,
        showDetail: false, borderColor: 'transparent', backgroundColor: palette.sliderBg, fillerColor: palette.accentSoft,
        handleStyle: { color: palette.sliderHandleBg, borderColor: palette.accent }, moveHandleStyle: { color: '#9aabc2' },
        emphasis: { moveHandleStyle: { color: palette.accent }, handleLabel: { show: false } }, dataBackground: { lineStyle: { color: palette.axisLine }, areaStyle: { color: palette.sliderArea } },
        selectedDataBackground: { lineStyle: { color: palette.accent }, areaStyle: { color: palette.sliderSelectedArea } },
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
        lineStyle: { color: palette.segment, width: 2.4 },
      },
      {
        name: '候选线段', type: 'line', data: layers.segments ? candidateSegmentPoints : [], connectNulls: true,
        showSymbol: false, silent: true, z: 4,
        lineStyle: { color: palette.segmentCandidate, width: 2, type: 'dashed' },
      },
      {
        name: '分型', type: 'scatter',
        data: layers.fractals ? analysis.fractals.map((item) => [item.time, item.price, item.type]) : [],
        symbol: 'triangle',
        symbolSize: 8,
        symbolRotate: (value: unknown) => ((value as [string, number, string])[2] === 'bottom' ? 180 : 0),
        itemStyle: {
          color: (params) => ((params.value as [string, number, string])[2] === 'top' ? GOLD : '#7294aa'),
          borderColor: palette.fractalBorder,
          borderWidth: 1,
        },
        silent: true,
        z: 6,
      },
      {
        name: '笔中枢', type: 'line', data: [], silent: true, z: 3,
        markArea: {
          silent: true,
          itemStyle: { color: 'rgba(125, 104, 168, 0.12)', borderColor: 'rgba(125, 104, 168, 0.55)', borderWidth: 1 },
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
          show: true, position: 'top', align: 'right', distance: 3, color: palette.zhongshuText, fontSize: 10, fontWeight: 600,
          backgroundColor: palette.zhongshuBg, borderColor: 'rgba(125, 104, 168, 0.35)', borderWidth: 1,
          borderRadius: 3, padding: [2, 4],
        },
      },
      {
        name: '买卖点', type: 'scatter', z: 8,
        data: layers.points ? signals.points.map((point) => ({
          value: [point.time || '', point.price],
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
          itemStyle: { color: point.histogram >= 0 ? palette.macdUp : palette.macdDown },
        })),
      },
      {
        name: 'DIF', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: macd.map((point) => point.dif),
        showSymbol: false, silent: true, lineStyle: { color: palette.dif, width: 1 },
      },
      {
        name: 'DEA', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: macd.map((point) => point.dea),
        showSymbol: false, silent: true, lineStyle: { color: GOLD, width: 1 },
      },
      {
        name: '成交量', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, barMaxWidth: 10,
        data: candles.map((item) => ({
          value: item.volume,
          itemStyle: { color: item.close >= item.open ? palette.volUp : palette.volDown },
        })),
      },
    ],
  }
}

export interface MarketChartProps {
  candles: Candle[]
  analysis: ChanAnalysis
  macd: MacdPoint[]
  signals: ChanSignals
  timeframe: string
  layers: ChartLayersState
  decimals: number
  theme?: ResolvedTheme
}

export default function MarketChart({
  candles,
  analysis,
  macd,
  signals,
  timeframe,
  layers,
  decimals,
  theme = 'light',
}: MarketChartProps) {
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
    if (!chartRef.current) return
    chartRef.current.setOption(buildOption(candles, analysis, macd, signals, timeframe, layers, decimals, theme), { notMerge: true })
  }, [candles, analysis, macd, signals, timeframe, layers, decimals, theme])

  return <div className="market-chart" ref={containerRef} role="img" aria-label={`${timeframe} 周期 K 线、成交量、MACD 与缠论结构图`} />
}

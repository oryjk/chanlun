import { buildSparklineGeometry } from '../../domain/market.ts'

export interface SparklineSvgProps {
  values: ReadonlyArray<number>
  width?: number
  height?: number
  label?: string
}

export default function SparklineSvg({ values, width = 68, height = 24, label = '近期走势' }: SparklineSvgProps) {
  const geom = buildSparklineGeometry(values, width, height)
  if (!geom) {
    return <span className="sparkline-empty">—</span>
  }
  return (
    <svg
      className={`asset-sparkline ${geom.tone}`}
      viewBox={`0 0 ${geom.width} ${geom.height}`}
      width={geom.width}
      height={geom.height}
      role="img"
      aria-label={label}
    >
      <path d={geom.areaPath} className="sparkline-area" />
      <polyline points={geom.polyline} className="sparkline-line" fill="none" />
      <circle cx={geom.lastX} cy={geom.lastY} r="1.8" className="sparkline-dot" />
    </svg>
  )
}

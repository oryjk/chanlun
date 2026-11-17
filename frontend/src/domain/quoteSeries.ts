import type { QuotePoint, QuoteSeries } from './types.ts'

export function mergeQuoteSeries(...seriesList: QuoteSeries[]): QuoteSeries {
  const points = new Map<number, QuotePoint>()
  for (const series of seriesList) {
    for (const point of series.points) {
      points.set(new Date(point.time).getTime(), point)
    }
  }
  return {
    secid: seriesList[seriesList.length - 1].secid,
    points: [...points.entries()]
      .sort(([first], [second]) => first - second)
      .map(([, point]) => point),
  }
}

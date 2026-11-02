export function mergeQuoteSeries(...seriesList) {
  const points = new Map()
  for (const series of seriesList) {
    for (const point of series.points) {
      points.set(new Date(point.time).getTime(), point)
    }
  }
  return {
    secid: seriesList.at(-1).secid,
    points: [...points.entries()]
      .sort(([first], [second]) => first - second)
      .map(([, point]) => point),
  }
}

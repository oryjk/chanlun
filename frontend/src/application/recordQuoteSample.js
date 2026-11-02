export function createRecordQuoteSample(gateway) {
  return (quote, signal) => gateway.recordQuoteSample(quote, signal)
}

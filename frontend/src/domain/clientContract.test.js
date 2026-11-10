import { describe, expect, test } from 'bun:test'
import contract from '../../../tests/fixtures/client_contract.json'
import { evaluateActionConclusion } from './signals.js'

describe('H5 and native shared signal contract', () => {
  for (const sample of contract.cases) {
    test(sample.name, () => {
      const result = evaluateActionConclusion({
        bias: sample.bias,
        latestPrice: sample.latest,
        latestPoint: sample.point,
        signalQuality: sample.quality,
      })
      expect(result.label).toBe(sample.expectedLabel)
    })
  }
})

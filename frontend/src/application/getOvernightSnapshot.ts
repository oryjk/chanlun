import type { GetOvernightSnapshot, MethodsReader } from './ports.ts'

export function createGetOvernightSnapshot(reader: MethodsReader): GetOvernightSnapshot {
  return (signal) => reader.getOvernightSnapshot(signal)
}

export async function getJson(path, signal) {
  try {
    const response = await fetch(path, { signal })
    const body = await response.json().catch(() => null)
    if (!response.ok) throw new Error(body?.detail || `请求失败 (${response.status})`)
    return body
  } catch (error) {
    if (error.name === 'AbortError') throw error
    if (error instanceof TypeError) throw new Error('无法连接行情服务，请检查后端是否启动')
    throw error
  }
}

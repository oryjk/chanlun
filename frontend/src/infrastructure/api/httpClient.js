async function requestJson(path, { method = 'GET', body: requestBody, signal } = {}) {
  try {
    const response = await fetch(path, {
      method,
      signal,
      headers: requestBody ? { 'Content-Type': 'application/json' } : undefined,
      body: requestBody ? JSON.stringify(requestBody) : undefined,
    })
    const body = await response.json().catch(() => null)
    if (!response.ok) throw new Error(body?.detail || `请求失败 (${response.status})`)
    return body
  } catch (error) {
    if (error.name === 'AbortError') throw error
    if (error instanceof TypeError) throw new Error('无法连接行情服务，请检查后端是否启动')
    throw error
  }
}

export function getJson(path, signal) {
  return requestJson(path, { signal })
}

export function postJson(path, body, signal) {
  return requestJson(path, { method: 'POST', body, signal })
}

// 部署在子路径（如 /chanlun/）时，API 同样位于该路径下
const API_ROOT = (import.meta.env?.BASE_URL || '/').replace(/\/$/, '')

export interface HttpError extends Error {
  status?: number
}

export interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  signal?: AbortSignal
  headers?: Record<string, string>
}

async function requestJson<T>(path: string, { method = 'GET', body: requestBody, signal, headers: extraHeaders }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = extraHeaders ? { ...extraHeaders } : {}
  if (requestBody !== undefined && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  try {
    const response = await fetch(`${API_ROOT}${path}`, {
      method,
      signal,
      headers: Object.keys(headers).length > 0 ? headers : undefined,
      body: requestBody !== undefined ? JSON.stringify(requestBody) : undefined,
    })
    const body = (await response.json().catch(() => null)) as { detail?: string } | null
    if (!response.ok) {
      const error: HttpError = new Error(body?.detail || `请求失败 (${response.status})`)
      error.status = response.status
      throw error
    }
    return body as T
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    if (error instanceof TypeError) throw new Error('无法连接行情服务，请检查后端是否启动')
    throw error
  }
}

export function getJson<T>(path: string, signal?: AbortSignal, options: Omit<RequestOptions, 'method' | 'body' | 'signal'> = {}): Promise<T> {
  return requestJson<T>(path, { ...options, signal })
}

export function postJson<T>(path: string, body: unknown, signal?: AbortSignal, options: Omit<RequestOptions, 'method' | 'body' | 'signal'> = {}): Promise<T> {
  return requestJson<T>(path, { ...options, method: 'POST', body, signal })
}

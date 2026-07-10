const API_BASE = 'http://localhost:8080/api'
const TOKEN_KEY = 'guild_points_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

export async function apiClient<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body: payload, headers, method = 'GET', ...rest } = options
  const token = getToken()

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    ...rest,
    headers: {
      ...(payload !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
  })

  if (!response.ok) {
    let message = response.statusText
    try {
      const err = (await response.json()) as { message?: string }
      if (err.message) message = err.message
    } catch {
      /* ignore */
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export { API_BASE }

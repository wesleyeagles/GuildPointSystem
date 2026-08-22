import { useMutation } from '@tanstack/react-query'
import { ApiError, API_BASE, getToken } from '@/Shared/api/client'
import { formatApiError } from '@/Shared/utils/formatApiError'

interface UploadResponse {
  url: string
}

export function useUploadImage() {
  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('type', 'item')

      const token = getToken()
      const response = await fetch(`${API_BASE}/uploads`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      })

      if (!response.ok) {
        let message = response.statusText
        try {
          const err = (await response.json()) as { message?: string }
          if (err.message) message = err.message
        } catch {
          /* ignore */
        }
        throw new ApiError(formatApiError(message), response.status)
      }

      const data = (await response.json()) as UploadResponse
      return data.url
    },
  })
}

import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { DllConnections } from './DllConnections'

const state = vi.hoisted(() => ({ data: [
  { memberId: 1, nickname: 'Alpha', character: 'Alpha', ip: '203.0.113.7', lastUploadAt: '2026-11-23T20:00:00Z', dllVersion: '1.52.1', sameIpCharacters: 2 },
  { memberId: 2, nickname: 'Beta', character: 'Beta', ip: '203.0.113.7', lastUploadAt: '2026-11-23T20:00:00Z', dllVersion: '1.52.1', sameIpCharacters: 2 },
  { memberId: 3, nickname: 'Solo', character: 'Solo', ip: '203.0.113.8', lastUploadAt: '2026-11-23T20:00:00Z', dllVersion: '1.52.1', sameIpCharacters: 1 },
], isPending: false, isFetching: false, error: null as Error | null, dataUpdatedAt: 1795464000000, refetch: vi.fn() }))
vi.mock('@/Domain/Dll/hooks/useDll', () => ({ useDllConnections: () => state }))
beforeEach(() => { state.error = null; state.refetch.mockClear() })
it('shows shared IPs by default, permits all recent uploads and refreshes only on request', () => {
  render(<DllConnections />)
  const table = screen.getByRole('table')
  expect(within(table).getByText('Alpha')).toBeInTheDocument()
  expect(within(table).getByText('Beta')).toBeInTheDocument()
  expect(within(table).queryByText('Solo')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('checkbox', { name: 'Somente IPs compartilhados' }))
  expect(within(table).getByText('Solo')).toBeInTheDocument()
  expect(state.refetch).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Atualizar conexões' }))
  expect(state.refetch).toHaveBeenCalledTimes(1)
})
it('does not turn a failed API request into a claim that no shared IP exists', () => {
  state.error = new Error('403')
  render(<DllConnections />)
  expect(screen.getByRole('alert')).toHaveTextContent('403')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

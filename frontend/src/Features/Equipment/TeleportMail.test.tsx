import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MemberEquipment } from '@/Domain/Equipment/types'
import { TeleportMail, TeleportMailDialog } from './TeleportMail'

const state = vi.hoisted(() => ({ start: vi.fn(), control: vi.fn(), error: null as Error | null, simulation: false, batches: [] as object[] }))
vi.mock('@/Domain/Equipment/hooks/useTeleportMail', async original => ({
  ...await original<object>(),
  useTeleportMail: () => ({ batches: { data: { batches: state.batches, simulation: state.simulation }, isPending: false, refetch: vi.fn() },
    start: { mutateAsync: state.start, isPending: false, error: state.error }, control: { mutate: state.control, isPending: false } }),
}))
const member = (id: number, nickname: string, level: number): MemberEquipment => ({ memberId: id, nickname, level, slots: [] })
const members = [member(1, 'Alpha', 50), member(2, 'Beta', 50), member(3, 'Level49', 49), member(4, 'Level51', 51), member(5, 'meow', 50)]
beforeEach(() => { state.start.mockReset().mockResolvedValue({}); state.control.mockClear(); state.error = null; state.batches = []; state.simulation = false })
afterEach(() => { vi.unstubAllEnvs(); cleanup(); localStorage.removeItem('teleport-mail-sender') })
function open() { render(<TeleportMail members={members} allowed />); fireEvent.click(screen.getByRole('button', { name: 'Enviar teleportes' })) }
describe('Lote de teleportes', () => {
  it('preselects exactly level 50, excludes the sender and permits editing both quantities', async () => {
    open()
    const targets = within(screen.getByRole('group', { name: 'Destinatários nível 50' }))
    expect(targets.getByLabelText('Alpha')).toBeChecked()
    expect(targets.getByLabelText('Beta')).toBeChecked()
    expect(targets.queryByText('Level49')).not.toBeInTheDocument()
    expect(targets.queryByText('Level51')).not.toBeInTheDocument()
    expect(targets.queryByText('meow')).not.toBeInTheDocument()
    fireEvent.click(targets.getByLabelText('Beta'))
    fireEvent.change(screen.getByLabelText('Remetente dos teleportes'), { target: { value: 'OutroSender' } })
    fireEvent.change(screen.getByLabelText('Quantidade de Taraven Teleport Scroll'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Quantidade de Ether Scroll (Cora)'), { target: { value: '4' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Conferir e iniciar envios' })) })
    expect(state.start).toHaveBeenCalledOnce()
    expect(state.start).toHaveBeenCalledWith(expect.objectContaining({ sender: 'OutroSender', recipients: ['Alpha'], items: [{ kind: 'taraven', quantity: 3 }, { kind: 'ether', quantity: 4 }] }))
    expect(localStorage.getItem('teleport-mail-sender')).toBe('OutroSender')
  })
  it('includes a trimmed optional subject and omits it when blank for title 1', async () => {
    open()
    expect(screen.getByLabelText('Assunto da carta')).toHaveAttribute('placeholder', '1')
    fireEvent.change(screen.getByLabelText('Assunto da carta'), { target: { value: '  Teleportes  ' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Conferir e iniciar envios' })) })
    expect(state.start).toHaveBeenCalledWith(expect.objectContaining({ subject: 'Teleportes' }))
    cleanup(); state.start.mockClear()
    open()
    fireEvent.change(screen.getByLabelText('Assunto da carta'), { target: { value: '   ' } })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Conferir e iniciar envios' })) })
    expect(state.start.mock.calls[0][0]).not.toHaveProperty('subject')
  })
  it('blocks an empty selection and invalid quantities', () => {
    open()
    fireEvent.click(screen.getByRole('button', { name: 'Desmarcar todos' }))
    expect(screen.getByRole('button', { name: 'Conferir e iniciar envios' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Marcar todos' }))
    fireEvent.change(screen.getByLabelText('Quantidade de Ether Scroll (Cora)'), { target: { value: '256' } })
    expect(screen.getByRole('button', { name: 'Conferir e iniciar envios' })).toBeDisabled()
  })
  it('does not offer a silent retry after a failed/uncertain start', async () => {
    state.start.mockRejectedValue(new Error('Uncertain'))
    open()
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Conferir e iniciar envios' })) })
    expect(state.start).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: 'Conferir e iniciar envios' })).not.toBeInTheDocument()
  })
  it('requires reviewing an uncertain previous batch before authorizing a new one', () => {
    state.batches = [{ id: 'a', sender: 'meow', state: 'uncertain', createdAt: 1, letters: [], error: 'Confira no jogo' }]
    open()
    expect(screen.getByRole('button', { name: 'Conferir e iniciar envios' })).toBeDisabled()
    fireEvent.click(screen.getByLabelText('Conferi o último envio no jogo e revisei os destinatários deste novo lote.'))
    expect(screen.getByRole('button', { name: 'Conferir e iniciar envios' })).toBeEnabled()
  })
})

describe('Recompensa do evento', () => {
  it('includes any level, sends only ipcss04 kind and snapshots selected claims', async () => {
    render(<TeleportMailDialog allowed close={() => {}} members={members} reward={{ name: 'PBs Major', claims: { Alpha: [11], Beta: [12], Level49: [13], Level51: [14], meow: [15] } }} />)
    const targets=within(screen.getByRole('group',{name:'Destinatários do evento'}))
    expect(targets.getByLabelText('Level49')).toBeChecked()
    expect(targets.getByLabelText('Level51')).toBeChecked()
    fireEvent.click(targets.getByLabelText('Beta'))
    expect(screen.queryByLabelText('Ether Scroll (Cora)')).not.toBeInTheDocument()
    await act(async()=>{fireEvent.click(screen.getByRole('button',{name:'Conferir e iniciar envios'}))})
    expect(state.start).toHaveBeenCalledWith(expect.objectContaining({items:[{kind:'cash499',quantity:10}],
      reward:{name:'PBs Major',claims:{Alpha:[11],Level49:[13],Level51:[14],meow:[15]}}}))
  })
  it('disables recipients whose claims were sent or became uncertain, including renamed players', () => {
    state.batches=[{id:'old',state:'uncertain',sender:'boi',createdAt:1,letters:[{recipient:'old-nick',claimIds:[11],state:'sent',tracking:'a',quantity:10,name:'CC',itemCode:'ipcss04'}]}]
    render(<TeleportMailDialog allowed close={() => {}} members={[member(1,'Alpha',45),member(2,'Beta',40)]}
      reward={{name:'PBs Major',claims:{Alpha:[11],Beta:[12]}}} />)
    const targets=within(screen.getByRole('group',{name:'Destinatários do evento'}))
    expect(targets.getByLabelText(/Alpha/)).toBeDisabled()
    expect(targets.getByLabelText('Beta')).toBeChecked()
  })
})

it('lets an admin cancel an unused planned reservation without sending a letter', () => {
  state.batches=[{id:'planned',state:'planned',sender:'boi',createdAt:1,letters:[]}]
  render(<TeleportMailDialog allowed close={() => {}} members={[]} reward={{name:'PBs Major',claims:{}}} />)
  fireEvent.click(screen.getByRole('button',{name:'Cancelar reserva'}))
  expect(state.control).toHaveBeenCalledWith({id:'planned',action:'cancel'})
  expect(state.start).not.toHaveBeenCalled()
})

it('labels simulation explicitly and blocks real mode when the local preview requires simulation', () => {
  vi.stubEnv('VITE_MAIL_SIMULATION_ONLY','true')
  render(<TeleportMailDialog allowed close={() => {}} members={[member(1,'Alpha',40)]} reward={{name:'PBs Major',claims:{Alpha:[1]}}} />)
  expect(screen.getByRole('alert')).toHaveTextContent('Envios reais estão bloqueados')
  fireEvent.click(screen.getByRole('button',{name:'Conferir e iniciar envios'}))
  expect(state.start).not.toHaveBeenCalled()
  cleanup();state.simulation=true
  render(<TeleportMailDialog allowed close={() => {}} members={[member(1,'Alpha',40)]} reward={{name:'PBs Major',claims:{Alpha:[1]}}} />)
  expect(screen.getByRole('status')).toHaveTextContent('MODO SIMULADO')
  expect(screen.getByRole('button',{name:'Iniciar simulação'})).toBeEnabled()
})

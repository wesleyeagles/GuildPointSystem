import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { EventRewardMail } from './EventRewardMail'
const state=vi.hoisted(()=>({admin:true,api:vi.fn()}))
vi.mock('@/Features/Auth/contexts/AuthContext',()=>({useAuthContext:()=>({hasRole:()=>state.admin})}))
vi.mock('@/Shared/api/client',()=>({apiClient:state.api}))
vi.mock('@/Features/Equipment/TeleportMail',()=>({TeleportMailDialog:({members,reward}:{members:{nickname:string}[];reward:{name:string}})=><div>{reward.name}: {members.map(m=>m.nickname).join(', ')}</div>}))
beforeEach(()=>{state.admin=true;state.api.mockReset()})
afterEach(cleanup)
function show(){render(<QueryClientProvider client={new QueryClient()}><EventRewardMail claimIds={[11,12]} /></QueryClientProvider>)}
it('only loads confirmed recipients when an admin opens the event reward',async()=>{
  state.api.mockResolvedValue({name:'PBs Major',recipients:[{memberId:1,nickname:'Alpha',claimIds:[11]},{memberId:2,nickname:'Beta',claimIds:[12]}]})
  show();expect(state.api).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button',{name:'Enviar recompensa'}))
  await screen.findByText('PBs Major: Alpha, Beta')
  expect(state.api).toHaveBeenCalledWith('/events/claims/recipients',{method:'POST',body:{claimIds:[11,12]}})
})
it('hides the action from members and never loads recipients',()=>{
  state.admin=false;show();expect(screen.queryByRole('button')).not.toBeInTheDocument();expect(state.api).not.toHaveBeenCalled()
})
it('stops on denied or missing recipients without offering the sending form',async()=>{
  state.api.mockRejectedValue(new Error('Atualize o evento'))
  show();fireEvent.click(screen.getByRole('button',{name:'Enviar recompensa'}))
  await waitFor(()=>expect(screen.getByRole('alert')).toHaveTextContent('Atualize o evento'))
})

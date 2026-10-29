import { useState } from 'react'
import { useDeleteItem } from '@/Domain/Item/hooks/useItems'
import { useGuildInventory } from '@/Domain/Item/hooks/useGuildInventory'
import { useItemSeeds } from '@/Domain/Seed/hooks/useItemSeeds'
import { useAuthContext } from '@/Features/Auth/contexts/AuthContext'
import { useAppToast } from '@/Shared/ui/components/AppToast/AppToast'
import { Button } from '@/Shared/ui/components/Button/Button'
import { Panel } from '@/Shared/ui/components/Panel/Panel'
import { CreateItemForm } from '@/Features/Items/components/CreateItemForm/CreateItemForm'
import { GuildInventoryList } from '@/Features/Items/components/GuildInventoryList/GuildInventoryList'
import './Items.styles.scss'

export { ItemCard } from '@/Features/Items/components/ItemCard/ItemCard'

export type ItemsView = 'list' | 'create'

interface ItemsPageProps {
  view: ItemsView
  /** Leaves the create form (after saving) and returns to the inventory list. */
  onShowList: () => void
}

/** Guild inventory and item admin; rendered as tabs of the auctions page. */
export function ItemsPage({ view, onShowList }: ItemsPageProps) {
  const { data: inventorySummary } = useGuildInventory({ page: 0, size: 1 })
  const totalInventory = inventorySummary?.totalElements ?? 0
  const { data: weaponCasts = [] } = useItemSeeds('WEAPON_CAST')
  const { hasRole } = useAuthContext()
  const { showToast } = useAppToast()
  const deleteItem = useDeleteItem()
  const isAdmin = hasRole('ADMINISTRADOR')

  const [editItemId, setEditItemId] = useState<number | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)

  const handleDelete = async (id: number) => {
    try {
      await deleteItem.mutateAsync(id)
      showToast('Item deletado.', 'success')
      setConfirmDelete(null)
    } catch {
      showToast('Erro ao deletar item.', 'error')
    }
  }

  return (
    <div className="items-page">
      {view === 'create' && isAdmin && <CreateItemForm onSuccess={onShowList} />}

      {view === 'list' && isAdmin && editItemId != null && (
        <CreateItemForm
          itemId={editItemId}
          onCancel={() => setEditItemId(null)}
          onSuccess={() => setEditItemId(null)}
        />
      )}

      {view === 'list' && editItemId == null && (
        <Panel title="Inventário da guild" code={`${totalInventory.toLocaleString('pt-BR')} ITM`}>
          <GuildInventoryList
            canAdmin={isAdmin}
            weaponCasts={weaponCasts}
            onEdit={
              isAdmin
                ? (id) => setEditItemId(id)
                : undefined
            }
            onDelete={(id) => setConfirmDelete(id)}
          />
        </Panel>
      )}

      {confirmDelete !== null && (
        <div className="items-confirm-overlay" onClick={() => setConfirmDelete(null)}>
          <div onClick={(e) => e.stopPropagation()}>
            <Panel title="Excluir item" variant="danger" className="items-confirm">
              <p>Confirmar exclusão do item?</p>
              <div className="items-confirm__actions">
                <Button variant="secondary" size="sm" onClick={() => setConfirmDelete(null)}>
                  Cancelar
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => handleDelete(confirmDelete)}
                  disabled={deleteItem.isPending}
                >
                  {deleteItem.isPending ? 'Deletando...' : 'Deletar'}
                </Button>
              </div>
            </Panel>
          </div>
        </div>
      )}
    </div>
  )
}

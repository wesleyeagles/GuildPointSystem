import type { LuaItemCategory } from '@/Domain/types/models'

/** 'OTHER' = items without a category (e.g. a MISC item created with only an image). */
export type InventoryCategory = LuaItemCategory | 'OTHER'

export const INVENTORY_CATEGORIES: { id: InventoryCategory; label: string }[] = [
  { id: 'WEAPON', label: 'Weapon' },
  { id: 'HELMET', label: 'Helmet' },
  { id: 'UPPER', label: 'Upper' },
  { id: 'LOWER', label: 'Lower' },
  { id: 'GLOVES', label: 'Gloves' },
  { id: 'SHOES', label: 'Shoes' },
  { id: 'AMULET', label: 'Amulet' },
  { id: 'RING', label: 'Ring' },
  { id: 'SHIELD', label: 'Shield' },
  { id: 'BOOTY', label: 'Booty' },
  { id: 'BOX', label: 'Box' },
  { id: 'POTION', label: 'Potion' },
  { id: 'RESOURCE', label: 'Resource' },
  { id: 'OTHER', label: 'Outros' },
]

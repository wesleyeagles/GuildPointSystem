---
name: talic-validation
description: Validates RF Online Talic upgrades for weapons and armor — percent tables, exclusivity rules, and subtype constraints. Use when creating/editing items, item_talic rows, or backend validation logic.
---

# Talic Validation

Spec §8.1.1 (Weapon) and §8.2.1 (Armor). Enforce in backend service before persisting `item_talic`.

## Weapon Talics

**Keen** — applies % bonus to Attack and Force Attack (min/max):

| Level | Bonus |
|---|---|
| 1 | 5% |
| 2 | 13% |
| 3 | 25% |
| 4 | 50% |
| 5 | 80% |
| 6 | 135% |
| 7 | 200% |

**Elemental (max 1 per weapon):** Sacredfire (Fire), Belief (Water), Guard (Earth), Glory (Wind).

Rules:
- Keen + one elemental allowed together.
- Only one elemental talic per item (`UNIQUE item_id + talic_type` helps).
- Levels 0–7; level 0 = not applied.

## Armor Talics

**Favor** — all subtypes, % on AvgDefPower (same table as Keen above).

**Subtype-exclusive (combine with Favor):**

| Talic | Subtype | L1→L7 values |
|---|---|---|
| Wisdom | Helmet | 10,15,20,25,30,35,50 % |
| Grace | Gloves | 10,20,30,45,60,75,100 (flat accuracy) |
| Darkness | Gloves | 2,5,10,20,30,40,50 % |
| Mercy | Shoes | 10,20,30,45,60,75,100 (flat dodge) |

Rules:
- Helmet: Favor + Wisdom OK.
- Gloves: Favor + Grace **or** Favor + Darkness (not both Grace and Darkness).
- Shoes: Favor + Mercy OK.
- Upper/Lower: Favor only.

## Validation checklist

```
- [ ] Level in 0..7
- [ ] Weapon: ≤1 elemental talic
- [ ] Armor: exclusive talic matches subtype
- [ ] Gloves: Grace XOR Darkness (not both)
- [ ] Computed stats use correct table (% vs flat)
- [ ] special_effects max 4 (JSONB array)
```

Store raw talic levels in `item_talic`; compute displayed stats in service/DTO layer.

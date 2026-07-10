---
name: feature-scaffold
description: Scaffolds new frontend features and backend packages following Guild Points monorepo conventions. Use when adding a new feature module, route, or backend domain package.
---

# Feature Scaffold

Follow spec §10 (frontend) and §11 (backend). Replace `{Feature}` / `{feature}` with PascalCase/camelCase name.

## Frontend scaffold

```
frontend/src/
├── Features/{Feature}/
│   ├── {Feature}.tsx
│   ├── {Feature}.types.ts
│   ├── {Feature}.styles.scss
│   ├── components/
│   ├── contexts/          # optional
│   └── utils/               # optional
└── Domain/{Feature}/hooks/
    └── use{Feature}.ts      # React Query: queries + mutations
```

**use{Feature}.ts template:**

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const KEY = ['{feature}'];

export function use{Feature}List() {
  return useQuery({ queryKey: KEY, queryFn: () => fetch('/api/{feature}').then(r => r.json()) });
}

export function useCreate{Feature}() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: unknown) => fetch('/api/{feature}', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
```

Register route in app router; add WS subscription in hook if feature is real-time.

## Backend scaffold

```
backend/src/main/java/com/guild/app/{feature}/
├── {Feature}Controller.java    # @RestController, @PreAuthorize, DTOs
├── {Feature}Service.java       # @Transactional business logic
├── {Feature}Repository.java    # Spring Data JPA
├── entity/
└── dto/
```

**Controller pattern:**

```java
@RestController
@RequestMapping("/api/{feature}")
@RequiredArgsConstructor
public class {Feature}Controller {
    private final {Feature}Service service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public {Feature}Response create(@Valid @RequestBody Create{Feature}Request req) {
        return service.create(req);
    }
}
```

Add Flyway migration if new tables; log auditable actions via `AuditLogService`; publish WS events if real-time.

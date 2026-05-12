# DAO layer

JPA entities, Spring Data repositories, custom repository wrappers, persistence mappers, audit. See `.specify/memory/constitution.md` for the full architecture; this file lists only what's specific to this layer.

## Layer rules

- Called by `service/` only. MUST NOT call `service/` or `kubernetes/`.
- `@Transactional` is permitted here on repository wrapper methods where the data-access boundary is inherent.
- `@LogExecution` MUST be on every `@Repository` / `@Component`.

## Naming

- Entities: `*Entity` in `dao/entity/`. Pure data holders — no business logic, no service calls, no Lombok `@Builder` defaults that have side effects.
- Spring Data interfaces: `*JpaRepository` in `dao/jpa/`.
- Custom domain-mapping wrappers: `*Repository` in `dao/repository/`. These are the surface the service layer talks to.
- Persistence mappers: `Persistence*Mapper` in `dao/mapper/`. MapStruct `componentModel = "spring"`.

## Subpackages

| Path | Responsibility |
|---|---|
| `entity/` | JPA entity classes (`*Entity`) — pure data holders |
| `jpa/` | Spring Data interfaces (`*JpaRepository`) — direct JPA surface |
| `repository/` | Custom domain-mapping wrappers (`*Repository`) — what the service layer talks to |
| `mapper/` | MapStruct persistence mappers (`Persistence*Mapper`) — entity ↔ domain |
| `audit/` | Hibernate Envers audit revision listeners and queries |

## Schema invariants

- JPA `ddl-auto: validate`. Flyway owns the schema — entities MUST NOT drive table creation or alteration.
- Adding a column / table is a Flyway migration first, entity change second.

## Related specs

- `specs/database-and-migrations/spec.md` — vendor strategy, migration paths, naming.

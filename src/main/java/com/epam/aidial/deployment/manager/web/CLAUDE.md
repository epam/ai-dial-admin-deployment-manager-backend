# Web layer

REST controllers, DTOs, MapStruct DTO mappers, exception handlers, security filters, validation. See `.specify/memory/constitution.md` for the full architecture; this file lists only what's specific to this layer.

## Layer rules

- May call `service/`. MUST NOT call `dao/` or `kubernetes/` directly.
- `@Transactional` is forbidden here (constitution Principle II).
- `@LogExecution` MUST be on every `@RestController` / `@Controller` / `@Component`.

## Naming

- Request/response: `*Dto`, `*RequestDto`. Simple immutable shapes prefer Java `record` or Lombok `@Value`.
- DTO mappers: `*DtoMapper` in `web/mapper/`. MapStruct `componentModel = "spring"`. Add `subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION` only for polymorphic mappers.

## Subpackages

| Path | Responsibility |
|---|---|
| `controller/` | `@RestController` classes; one per top-level resource. Internal endpoints live in `controller/internal/` |
| `dto/` | Request/response shapes (`*Dto`, `*RequestDto`, records, `@Value`) |
| `mapper/` | MapStruct DTO mappers (`*DtoMapper`) — domain → DTO and back |
| `handler/` | `DefaultExceptionHandler`, `SseEmitter`-based streaming handlers |
| `security/` | OAuth2 / JWT filter chain, role mapping, `SecurityInfoController` plumbing |
| `validation/` | Custom Bean Validation constraints and validators |
| `utils/` | Web-only helpers (header parsing, pagination conversion, etc.) |

## API conventions

- Public base path: `/api/v1/`. Internal: `/api/internal/v1/`.
- Every endpoint method MUST have SpringDoc `@Operation(summary = "...")` and the relevant `@ApiResponse` annotations.
- List endpoints that may return large datasets MUST accept `Pageable`.
- Real-time streaming uses `SseEmitter` — see existing handlers in `web/handler/`.
- Errors flow through `DefaultExceptionHandler` and serialize as `ErrorView` (`message`, `status`, `traceparent`, plus `path`, `method`, `error`).

## Related specs

- `specs/api-conventions/spec.md` — versioning, error schema, pagination.
- Per-capability specs in `specs/<capability>/spec.md` — domain endpoints.

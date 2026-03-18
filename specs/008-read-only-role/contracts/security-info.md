# Contract: Security Info Endpoint

## Endpoint

`GET /api/v1/security-info`

## Authentication

Required when `config.rest.security.mode=oidc`. Accessible by both FULL_ADMIN and READ_ONLY_ADMIN.

## Response (OIDC mode)

```json
{
  "userInfo": {
    "id": "user-principal-id",
    "email": "user@example.com",
    "roles": ["FULL_ADMIN"]
  }
}
```

## Response (none mode)

In "none" mode, Spring Security's anonymous authentication filter provides an anonymous user context:

```json
{
  "userInfo": {
    "id": "anonymousUser",
    "email": null,
    "roles": ["ROLE_ANONYMOUS"]
  }
}
```

## Response Codes

| Code | Condition |
|---|---|
| 200 | Authenticated user (OIDC) or any request (none mode) |
| 401 | Missing or invalid token |
| 403 | Authenticated but no valid role mapping |

## Notes

- `roles` contains application role names, not IdP role names
- `email` may be null if not present in token claims
- `id` is the principal claim value from the token
- In "none" mode, `id` is "anonymousUser" (Spring Security anonymous auth)

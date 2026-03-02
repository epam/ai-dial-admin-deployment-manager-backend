# Enabling Cilium for AI DIAL Deployment Manager

When `CILIUM_NETWORK_POLICIES_ENABLED` is set to `true`, the Deployment Manager allows you to enable Cilium network policies for image build and deployments.

In this case, **extra RBAC roles for Cilium are required** so that the Deployment Manager can create and manage `CiliumNetworkPolicy` and `CiliumClusterwideNetworkPolicy` resources in the target namespaces.

---

## Required RBAC

Create the following Role and RoleBinding in **each** namespace where the Deployment Manager will manage Cilium policies: `mcp-namespace`, `nim-namespace`, or `kserve-namespace`.

Replace `<mcp-namespace|nim-namespace|kserve-namespace>` with the actual target namespace for that manifest. Replace `<deployment-manager-sa-namespace>` with the namespace where the Deployment Manager ServiceAccount is located.

### Role

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: deployment-manager-cilium-role
  namespace: <mcp-namespace|nim-namespace|kserve-namespace>
rules:
  - verbs:
      - create
      - update
      - deletecollection
      - patch
      - get
      - delete
      - list
      - watch
    apiGroups:
      - cilium.io
    resources:
      - ciliumnetworkpolicies
      - ciliumclusterwidenetworkpolicies
  - verbs:
      - patch
      - update
    apiGroups:
      - cilium.io
    resources:
      - ciliumnetworkpolicies/status
      - ciliumclusterwidenetworkpolicies/status
```

### RoleBinding

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ai-dial-deployment-manager-cilium-role
  namespace: <mcp-namespace|nim-namespace|kserve-namespace>
subjects:
  - kind: ServiceAccount
    name: ai-dial-deployment-manager
    namespace: <deployment-manager-sa-namespace>
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: deployment-manager-cilium-role
```

---

## Summary

| Setting | Effect |
|--------|--------|
| `CILIUM_NETWORK_POLICIES_ENABLED=true` | Deployment Manager can enable Cilium network policies for image build and deployments |
| Required setup | Role + RoleBinding above in each target namespace (`mcp-namespace`, `nim-namespace`, `kserve-namespace`, etc.) |

Apply the manifests in each namespace where Cilium policies will be managed, then set `CILIUM_NETWORK_POLICIES_ENABLED=true` for the Deployment Manager.

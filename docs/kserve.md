# KServe Installation Guide

This document provides a step-by-step guide for installing KServe on your Kubernetes cluster. The steps are based on the official KServe documentation and include the installation of necessary components like Cert Manager and an Ingress Controller.

## Prerequisites

- A Kubernetes cluster with sufficient resources.
- Helm installed on your local machine.

## Installation Steps

### 1. Install Cert Manager

Cert Manager is required for managing TLS certificates in your cluster. Follow the official Cert Manager installation guide using Helm:

- Visit the Cert Manager installation documentation: [Cert Manager Installation Guide](https://cert-manager.io/docs/installation/helm/)

### 2. Install Ingress Controller

You need an Ingress Controller to manage external access to the services in your cluster. You can either install a new Ingress Controller or use an existing Ingress Class. Below is an example of an Ingress Class configuration for NGINX:

```yaml
apiVersion: networking.k8s.io/v1
kind: IngressClass
metadata:
  name: nginx
```

### 3. Install KServe

KServe is installed using Helm. Follow these steps to install the KServe Custom Resource Definitions (CRDs) and the KServe components:

- **Install KServe CRDs**:
  ```bash
  helm install kserve-crd oci://ghcr.io/kserve/charts/kserve-crd --version v0.15.0 -n your_namespace --create-namespace
  ```

- **Install KServe**:
  ```bash
  helm install kserve oci://ghcr.io/kserve/charts/kserve --version v0.15.0 \
    --set kserve.controller.deploymentMode=Standard \
    --set kserve.controller.gateway.ingressGateway.className=istio \
    -n your_namespace
  ```

Replace `your_namespace` with the desired namespace where you want to install KServe.

## Post-Installation

- Verify that all KServe components are running correctly by checking the pods in the namespace you installed KServe.
- Ensure that the Ingress Controller is properly configured to route traffic to KServe.

For more detailed configuration and usage, refer to the [KServe Documentation](https://kserve.github.io/website/docs/admin-guide/kubernetes-deployment).



Here's a README section that explains the application of necessary roles and role bindings for managing permissions within the Kubernetes cluster:

---

# Role and RoleBinding Configuration for AI DIAL Admin

This section describes the configuration of necessary roles and role bindings to manage permissions for the AI DIAL Admin components within the Kubernetes cluster. These configurations ensure that the appropriate access controls are in place for managing inference services and related resources.

## Role Configuration

The following Role configuration grants permissions to manage inference services and other resources within the `kserve-models` namespace.

### Role Manifest

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-deployment-role
  namespace: kserve-models
rules:
  - apiGroups:
      - 'serving.kserve.io'
    resources:
      - inferenceservices
    verbs:
      - get
      - list
      - watch
      - create
      - delete
  - apiGroups:
      - ''
    resources:
      - pods
      - pods/log
      - events
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - ''
    resources:
      - secrets
    verbs:
      - get
      - create
      - delete
```

## RoleBinding Configuration

The following RoleBinding configuration associates the role with a specific service account, granting it the defined permissions.

### RoleBinding Manifest

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ai-dial-admin-deployment-role
  namespace: kserve-models
subjects:
  - kind: ServiceAccount
    name: ai-dial-test-admin-deployment-manager-backend
    namespace: ai-dial-test
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: ai-dial-admin-deployment-role
```

### Applying the Role and RoleBinding

To apply these configurations to your Kubernetes cluster, follow these steps:

1. **Save the Manifests**: Copy the above YAML configurations into separate files, for example, `role.yaml` and `rolebinding.yaml`.

2. **Apply the Manifests**: Use the `kubectl` command-line tool to apply the manifests to your cluster. Run the following commands in your terminal:

   ```bash
   kubectl apply -f role.yaml
   kubectl apply -f rolebinding.yaml
   ```
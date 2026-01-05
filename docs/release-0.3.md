# Deployment Manager Release updates

This document outlines the necessary steps for renaming and updating the Deployment Manager services and related configurations.

## 1. Deployment Manager Backend Service Renaming

### 1. Artifact Relocation

- Artifacts have been moved from `ai-dial-admin-mcp-manager-backend` to `ai-dial-admin-deployment-manager-backend`.
- Ensure that your Helm references are updated to point to the new location.

#### Example Configuration

```yaml
image:
  pullPolicy: Always
  registry: registry-dev.deltixhub.com
  repository: ai/dial/ai-dial-admin-deployment-manager-backend
  tag: development
  pullSecrets:
    - epm-rtc-registry-dev
```

### 2. Secrets Renaming

- Rename all secrets if you are using CSI secret provider classes .

### 3. Service Accounts and Role Bindings

- Ensure that service accounts mapped to roles have appropriate naming. Update the RoleBinding configuration as follows:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ai-dial-admin-deployment-role
  namespace: ai-dial-test-deployment-manager
subjects:
  - kind: ServiceAccount
    name: ai-dial-test-deployment-manager-backend
    namespace: ai-dial-test
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: ai-dial-admin-deployment-role
```

## Note on Roles, Service Accounts, and Binding

### Untrusted Clusters

The roles, service accounts, and bindings are located on untrusted clusters. You can find them in the following GitLab repositories:

- [DIALX Cluster](https://gitlab.deltixhub.com/account-management/epm-dial/aws/975050352697/aws-eks-ext-deployment)
- [UAT Cluster](https://gitlab.deltixhub.com/account-management/epm-dial/aws/619071342561/eks-mcp)

These resources are situated in the `mcp-apps` and `mcp-build` namespaces.

### Updating Service Accounts

1. **Service Account Naming**: After applying a new name for the service account and regenerating the service account secret, ensure to update the `kubeconfig` secret in the trusted cluster with the new user token.

2. **Example Configuration for Untrusted Cluster**:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: dial-deployment-manager
  namespace: mcp-apps
---
apiVersion: v1
kind: Secret
metadata:
  name: dial-deployment-manager-sa-secret
  namespace: mcp-apps
  annotations:
    kubernetes.io/service-account.name: "dial-deployment-manager"
type: kubernetes.io/service-account-token
data:
  extra: ZHVtbXk=
```

### Trusted Cluster Example

For the trusted cluster, update the `kubeconfig` as follows:

- **Rename**: Change `dial-mcp-manager-kubeconfig` to `dial-deployment-manager-kubeconfig`.
- **Update User Token**: Ensure the user token in the `kubeconfig` is updated with the new token from the service account secret.

```yaml
apiVersion: v1
kind: Config
users:
- name: dial-deployment-manager
  user:
    token: <new-user-token>
```

### Important Steps

- Ensure that the service account and its secret are correctly configured in the `mcp-apps` namespace.
- After changes, update the `kubeconfig` in the trusted cluster to reflect the new service account token.

### 4. Update Federated Credentials (Azure Cloud)

- Add steps to update federated credentials if you are using Azure Cloud.

### 5. Volumes and Database Management

1. **Downscale MCP Manager Backend**: Set the MCP manager backend replicas to 0.
2. **Create Simple Deployment**: Refer to the PVC of the MCP manager.
### Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  namespace: dial-admin
  labels:
    app: nginx
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      volumes:
        - name: test-admin-db
          persistentVolumeClaim:
            claimName: test-admin-mcp-db
      containers:
      - name: nginx
        image: nginx:1.14.2
        ports:
        - containerPort: 80
        volumeMounts:
            - name: test-admin-db
              mountPath: /app/data/db
```
3. **Execute to Pod**: Access the pod to manage files. 
   ```bash
   kubectl exec -i -t -n dial-admin nginx-deployment-poduid -c nginx -- sh -c "clear; (bash || ash || sh)"
   ```
4. **Archive Database Folder**: Use the following command to archive the database:
   ```bash
   tar cf test.tar app/data/db
   ```
5. **Copy Archive to Local Machine**: Use the command:
   ```bash
   kubectl cp nginx-deployment-6f6886f789-r2nwg:test.tar ./test.tar -n dial-admin
   ```
6. **Upload and Rename Archive**: After renaming, upload the archive to the file system with the new name `deployment_manager_db.mv.db`:
   ```bash
   kubectl cp ./test.tar ai-dial-mcp-manager-backend-588b88fff8-tjgvd:../tmp -n dial-admin
   cd tmp #execute inside pod
   tar -xvf test.tar
   cd app/data/db
   mv mcp_manager_db.mv.db deployment_manager_db.mv.db
   mv tmp/app/data/db/deployment_manager_db.mv.db ../../../../app/data/db/deployment_manager_db.mv.db
   ```
7. **Switch MCP Manager to New Database**: Update the `H2_FILE` variable to point to the new database:
   - `H2_FILE: ./data/db/deployment_manager_db`
**Note**: In case of Azure Blob Storage, just rename the file inside the blob storage and update the `H2_FILE` variable accordingly.

### 6. Release Renaming

- Rename the release to reflect the new service name.

### 2. Deployment Manager Frontend Application (deprecated)

#### Changes:
1. **Artifact Relocation**:
   - Artifacts have been moved from `ai-dial-admin-mcp-manager-frontend` to **ai-dial-admin-deployment-manager-frontend**.
   - **Example Configuration**:
     ```yaml
     image:
       pullPolicy: Always
       registry: registry-dev.deltixhub.com
       repository: ai/dial/ai-dial-deployment-manager-frontend
       tag: development
       pullSecrets:
         - epm-rtc-registry-dev
     ```

2. **Release Renaming**:
   - Rename the release to reflect the new service name.

3. **API URL Update**:
   - Reset `PLUGIN_API_URL` to the new backend address.
   - **Example**:
     ```yaml
     env:
       PLUGIN_API_URL: "http://ai-dial-test-admin-deployment-manager-backend.ai-dial-test.svc.cluster.local:80/"
     ```

### 3. Admin Frontend

#### Changes:
1. **API URL Update**:
   - Update `DIAL_DEPLOYMENTS_API_URL` to the new value for the backend service.
   - **Example**:
     ```yaml
     env:
       DIAL_DEPLOYMENTS_API_URL: "http://ai-dial-test-admin-deployment-manager-backend.ai-dial-test.svc.cluster.local:80/"
     ```

### 4. Admin Backend

#### Changes:
1. **Client URL Update**:
   - Update `PLUGINS_DEPLOYMENT_MANAGER_CLIENT_URL` to the new value for the backend service.
   - **Example**:
     ```yaml
     env:
       PLUGINS_DEPLOYMENT_MANAGER_CLIENT_URL: "http://ai-dial-test-admin-deployment-manager-backend.ai-dial-test.svc.cluster.local:80/"
     ```


## 2. Deployment Manager NIM roles update


This document provides instructions for updating the NIM (Network Infrastructure Management) role in the Kubernetes cluster. The role is defined to manage specific resources within the `ai-dial-test-nim` namespace.

### Role Definition

The following Kubernetes Role is defined to manage resources related to NVIDIA services and general Kubernetes resources like pods and secrets.

### Role Configuration

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-mcp-role
  namespace: ai-dial-test-nim
rules:
  - apiGroups:
      - 'apps.nvidia.com'
    resources:
      - nimservices
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
### Instructions for Updating the Role

1. **Access the Kubernetes Cluster:**
   Ensure you have the necessary permissions to access and modify roles in the Kubernetes cluster.

2. **Apply the Role Configuration:**
   Save the above YAML configuration to a file, e.g., `ai-dial-admin-mcp-role.yaml`.

3. **Execute the Update Command:**
   Use the following command to apply the role configuration:

   ```bash
   kubectl apply -f ai-dial-admin-mcp-role.yaml
   ```

## 2. Deployment Manager Knative roles update


This document provides instructions for updating the Knative role in the Kubernetes cluster. The role is defined to manage specific resources within the `ai-dial-test-knative-services` namespace.

### Role Definition

The following Kubernetes Role is defined to manage resources related to knative services and general Kubernetes resources like pods and secrets.

### Role Configuration

```yaml
    apiVersion: rbac.authorization.k8s.io/v1
    kind: Role
    metadata:
      name: ai-dial-admin-deployment-role
      namespace: ai-dial-test-knative-services
    rules:
      - apiGroups:
          - 'serving.knative.dev'
        resources:
          - services
        verbs:
          - get
          - list
          - watch
          - create
          - update #was added
          - delete
      - apiGroups:
          - ""
        resources:
          - pods
          - pods/log
          - events
        verbs:
          - get
          - list
          - watch
          - delete
      - apiGroups:
          - ""
        resources:
          - secrets
        verbs:
          - get
          - create
          - delete


```
### Instructions for Updating the Role

1. **Access the Kubernetes Cluster:**
   Ensure you have the necessary permissions to access and modify roles in the Kubernetes cluster.

2. **Apply the Role Configuration:**
   Save the above YAML configuration to a file, e.g., `ai-dial-admin-deployment-role.yaml`.

3. **Execute the Update Command:**
   Use the following command to apply the role configuration:

   ```bash
   kubectl apply -f ai-dial-admin-mcp-role.yaml
   ```
## 4. Deployment Manager Network Policy Configuration

This section describes the configuration of a Network Policy to allow ingress traffic to the Deployment Manager backend from specific admin components. This setup ensures secure and controlled access within the Kubernetes cluster.

### Network Policy Details

The following Network Policy is designed to permit ingress traffic from the admin components to the Deployment Manager backend. This policy is defined using Kubernetes NetworkPolicy resources.

### Network Policy Configuration

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: admin-deployment-manager-backend-allow-policy-ingress
  namespace: '{{ template "common.names.namespace" . }}'
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/instance: <ai-dial-admin-deployment-manager-backend>
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app.kubernetes.io/instance: <ai-dial-admin-deployment-manager-frontend> #deprecated
    - podSelector:
        matchLabels:
          app.kubernetes.io/instance: <ai-dial-admin-backend>
    - podSelector:
        matchLabels:
          app.kubernetes.io/instance: <ai-dial-admin-frontend>
    ports:
    - protocol: TCP
      port: 8080
```

### Key Components

- **apiVersion**: Specifies the API version for the NetworkPolicy resource.
- **kind**: Defines the resource type, which is `NetworkPolicy`.
- **metadata**: Contains the name and namespace for the NetworkPolicy.
  - **name**: `admin-deployment-manager-backend-allow-policy-ingress` is the name of the policy.
  - **namespace**: Uses a templated namespace to ensure the policy is applied in the correct context.
- **spec**: Defines the specifications of the NetworkPolicy.
  - **podSelector**: Selects the pods to which this policy applies, identified by the label `app.kubernetes.io/instance: <ai-dial-admin-deployment-manager-backend>`.
  - **policyTypes**: Specifies that this policy applies to `Ingress` traffic.
  - **ingress**: Details the ingress rules.
    - **from**: Lists the sources allowed to send traffic to the selected pods.
      - **podSelector**: Matches pods by labels, allowing traffic from:
        - `<ai-dial-admin-deployment-manager-frontend>` (deprecated)
        - `<ai-dial-admin-backend>`
        - `<ai-dial-admin-frontend>`
    - **ports**: Specifies the allowed ports and protocols, with TCP traffic permitted on port 8080.

### Applying the Network Policy

To apply this Network Policy to your Kubernetes cluster, follow these steps:

1. **Save the Manifest**: Copy the above YAML configuration into a file, for example, `network-policy.yaml`.

2. **Apply the Manifest**: Use the `kubectl` command-line tool to apply the manifest to your cluster. Run the following command in your terminal:

   ```bash
   kubectl apply -f network-policy.yaml
   ```

## 5 Support for Trusted Private Git Repositories (optional)

This section describes the optional configuration for supporting trusted private Git repositories. This setup allows the service to clone code from specified private repositories during build operations.

### Step 1: Create a Secret

To enable access to private Git repositories, create a Kubernetes secret with two keys: `DeploymentMangerSSHKey` and `DeploymentManagerSSHHosts`. This secret will store the SSH key and known hosts information required for secure access.

#### Example: Creating the Secret

Use the following `kubectl` command to create the secret:

```bash
kubectl create secret generic ssh-secret \
  --from-file=DeploymentMangerSSHKey=/path/to/your/ssh/key \
  --from-file=DeploymentManagerSSHHosts=/path/to/your/known/hosts
```

Replace `/path/to/your/ssh/key` and `/path/to/your/known/hosts` with the actual paths to your SSH key and known hosts file.

### Step 2: Mount Secret as a Volume

Mount the created secret as a volume in your deployment to make the SSH key and known hosts accessible to the service.

#### Volume Configuration

Add the following configuration to your Helm values file:

```yaml
extraVolumes:
  - name: ssh-volume
    secret:
      secretName: ssh-secret

extraVolumeMounts:
  - name: ssh-volume
    readOnly: true
    mountPath: /mnt/ssh
```

### Step 3: Set Helm Variables

Configure the Helm environment variables to specify the trusted private Git repositories.

#### Helm Environment Variables

Add the following environment variable to your Helm values file:

```yaml
env:
  TRUSTED_PRIVATE_GIT_REPOS: '[{"host":"gitlab.deltixhub.com","sshKeyPath":"/mnt/ssh/DeploymentMangerSSHKey","sshKnownHostsPath":"/mnt/ssh/DeploymentManagerSSHHosts"}]'
```

#### Explanation

- **TRUSTED_PRIVATE_GIT_REPOS**: Specifies the trusted private Git repositories, including:
  - **host**: The Git repository host, e.g., `gitlab.deltixhub.com`.
  - **sshKeyPath**: Path to the SSH key, mounted from the secret.
  - **sshKnownHostsPath**: Path to the known hosts file, mounted from the secret.

#### Note

You can also configure HTTPS connections to the repository using the following format for `TRUSTED_PRIVATE_GIT_REPOS`:

```yaml
TRUSTED_PRIVATE_GIT_REPOS: '[{"host": "gitlab.deltixhub.com", "user": "user", "token": "token"}]'
```

- **user**: The username for accessing the repository.
- **token**: The personal access token for authentication.

### Applying the Configuration

Ensure that these configurations are included in your Helm values file before deploying or updating your application. This setup will enable secure access to the specified private Git repositories during build operations.

### Further Documentation

For more detailed information on Git configuration, please refer to the [official documentation](https://gitlab.deltixhub.com/Deltix/openai-apps/dial-admin/ai-dial-admin-mcp-manager-backend/-/blob/development/docs/configuration.md?ref_type=heads#git-configuration).

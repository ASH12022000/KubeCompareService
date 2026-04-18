# KubeCompareService — API Documentation

**Base URL:** `http://localhost:8080/api`  
**Auth:** All endpoints except `/auth/*` require `Authorization: Bearer <JWT>` header.  
**Content-Type:** `application/json` unless noted otherwise.

---

## Authentication

### `POST /api/auth/signup`
Register a new user. Sends a 6-digit OTP to the provided email.

**Request**
```json
{ "email": "user@company.com", "password": "secret123" }
```

**Response `200`**
```json
{ "message": "OTP sent to email" }
```

---

### `POST /api/auth/verify`
Verify email OTP after signup. Must be completed before login is allowed.

**Request**
```json
{ "email": "user@company.com", "otp": "482910" }
```

**Response `200`**
```json
{ "message": "Verified. You can now log in." }
```
**Response `401`**
```json
{ "error": "Invalid OTP" }
```

---

### `POST /api/auth/login`
Authenticate a verified user. Returns a JWT and the user's MongoDB `id`.

**Request**
```json
{ "email": "user@company.com", "password": "secret123" }
```

**Response `200`**
```json
{
  "token":  "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "6621f3a9b3e7c10012345678",
  "email":  "user@company.com"
}
```
**Response `401`**
```json
{ "error": "Invalid credentials or account not verified" }
```

> JWT subject = `email`. Expiry configured via `app.security.jwt-expiration-ms`.

---

### `POST /api/auth/forgot-password`
Sends a UUID reset token to the email. Always returns `200` to prevent enumeration.

**Request**
```json
{ "email": "user@company.com" }
```
**Response `200`**
```json
{ "message": "If this email is registered, a reset link has been sent." }
```

---

### `POST /api/auth/reset-password`
**Request**
```json
{ "email": "user@company.com", "token": "a3f1c2d4-...", "newPassword": "newSecret456" }
```
**Response `200`** `{ "message": "Password reset successfully." }`  
**Response `400`** `{ "error": "Invalid or expired token." }`

---

## Comparison

### `POST /api/comparison/connect`
Test connectivity to a cluster.

**Request**
```json
{
  "type": "JUMP",
  "clusterUrl": "",
  "encryptedToken": "",
  "jumpHost": "10.0.0.1",
  "jumpUser": "root",
  "encryptedJumpPassword": "mysshpassword"
}
```
**Response `200`** `{ "status": "SUCCESS" }`  
**Response `500`** `{ "status": "ERROR", "message": "..." }`

---

### `POST /api/comparison/run`
Run a live comparison between two clusters. Auto-saves to history.

**Request**
```json
{
  "userId": "6621f3a9b3e7c10012345678",
  "ns1": "production",
  "ns2": "staging",
  "checks": ["DEPLOYMENTS","CONFIGMAPS","SERVICES","PVC","IMAGES","VIRTUALSERVICES","AUTH_POLICY"],
  "env1": { "type": "JUMP", "jumpHost": "10.0.0.1", "jumpUser": "root", "encryptedJumpPassword": "pass1" },
  "env2": { "type": "JUMP", "jumpHost": "10.0.0.2", "jumpUser": "root", "encryptedJumpPassword": "pass2" }
}
```
**Response `200`**
```json
{
  "deployments": [{ "name": "my-app", "status": "MATCH|MISMATCH|ONLY_IN_1|ONLY_IN_2", "diff": {} }],
  "configmaps": [...],
  "services":   [...],
  "pvcs":       [...],
  "images":     [...],
  "istio":      [...]
}
```
**Response `500`** `{ "error": "..." }`

---

### `GET /api/comparison/history/{userId}`
Last 10 comparisons for a user, newest first.

**Response `200`**
```json
[{
  "id": "664abc...",
  "userId": "6621f3...",
  "timestamp": "2026-04-18T12:00:00",
  "primaryClusterUrl": "jump://10.0.0.1",
  "comparisonClusterUrl": "jump://10.0.0.2",
  "primaryNamespace": "production",
  "comparisonNamespace": "staging",
  "status": "SUCCESS",
  "results": [{ "category": "deployments", "match": false, "details": [...] }]
}]
```

---

### `DELETE /api/comparison/history/{userId}/{id}`
Delete a history record. Enforces ownership.

**Response `200`** empty  
**Response `403`** `"Forbidden"`  
**Response `404`** not found

---

### `GET /api/comparison/export/{historyId}`
Export history record as PDF.

**Response `200`**  
`Content-Type: application/pdf` | `Content-Disposition: attachment; filename=report.pdf`  
**Response `404`** not found

---

## Baselines

### `GET /api/baselines/user/{userId}`
All golden baselines for a user.

**Response `200`**
```json
[{
  "id": "665xyz...",
  "userId": "6621f3...",
  "name": "Production Baseline — Apr 2026",
  "environmentId": "10.0.0.1:production",
  "namespace": "production",
  "jumpHost": "10.0.0.1",
  "jumpUser": "root",
  "clusterUrl": "",
  "timestamp": "2026-04-15T08:00:00",
  "resourceSpecs": { "deployments": [...], "configmaps": [...] }
}]
```

---

### `GET /api/baselines/check?userId={userId}&environmentId={environmentId}`
Check if a baseline exists for this user+environment.

**Response `200` — exists:** `{ "exists": true, "id": "...", "name": "..." }`  
**Response `200` — not found:** `{ "exists": false }`

---

### `POST /api/baselines/save`
Capture a live snapshot and save as a baseline. Returns `409` on duplicate (unless `override: true`).

**Request**
```json
{
  "userId": "6621f3...",
  "name": "Production Baseline — Apr 2026",
  "namespace": "production",
  "jumpHost": "10.0.0.1",
  "jumpUser": "root",
  "jumpPassword": "mysshpass",
  "clusterUrl": "",
  "checks": ["DEPLOYMENTS","CONFIGMAPS","SERVICES","IMAGES"],
  "override": false
}
```
**Response `200`** — saved `BaselineSnapshot`  
**Response `409`**
```json
{
  "conflict": true,
  "message": "A baseline already exists for this environment.",
  "existingId": "665xyz...",
  "existingName": "Production Baseline"
}
```
> Re-send with `"override": true` to replace the existing one.

**Response `500`** `{ "error": "..." }`

---

### `DELETE /api/baselines/{userId}/{id}`
Delete a baseline. Enforces ownership.

**Response `200`** empty | **`403`** Forbidden | **`404`** not found

---

### `POST /api/baselines/compare/{snapshotId}`
Compare live cluster state against a saved baseline. Reads connection details from the stored snapshot — no credentials required in request body.

**Request** `{}`

**Response `200`**
```json
{
  "deployments": [{ "name": "api-server", "status": "MISMATCH", "diff": { "image": { "baseline": "v1.0", "live": "v1.1" } } }],
  "configmaps": [...]
}
```
**Response `500`** `{ "error": "Baseline snapshot not found: ..." }`

---

## Data Models

### `SavedEnvironment` (inline in requests)
| Field | Type | Notes |
|---|---|---|
| `type` | `String` | `"DIRECT"` or `"JUMP"` |
| `clusterUrl` | `String` | K8s API URL (Direct mode) |
| `encryptedToken` | `String` | Service account bearer token |
| `jumpHost` | `String` | SSH jump host |
| `jumpUser` | `String` | SSH username |
| `encryptedJumpPassword` | `String` | SSH password |

### `ComparisonHistory`
| Field | Type | Notes |
|---|---|---|
| `id` | `String` | MongoDB `_id` |
| `userId` | `String` | Owner |
| `timestamp` | `String` | ISO-8601 |
| `primaryClusterUrl` | `String` | `jump://host` or API URL |
| `comparisonClusterUrl` | `String` | |
| `primaryNamespace` | `String` | |
| `comparisonNamespace` | `String` | |
| `status` | `String` | `"SUCCESS"` \| `"ERROR"` |
| `results` | `CategoryResult[]` | |

### `CategoryResult`
| Field | Type | Notes |
|---|---|---|
| `category` | `String` | e.g. `"deployments"` |
| `match` | `boolean` | `true` = no diffs |
| `details` | `Object` | Raw diff list |

### `BaselineSnapshot`
| Field | Type | Notes |
|---|---|---|
| `id` | `String` | MongoDB `_id` |
| `userId` | `String` | Owner |
| `name` | `String` | User label |
| `environmentId` | `String` | `"{jumpHost}:{namespace}"` |
| `namespace` | `String` | |
| `clusterUrl` | `String` | |
| `jumpHost` | `String` | |
| `jumpUser` | `String` | |
| `timestamp` | `String` | ISO-8601 |
| `resourceSpecs` | `Map<String,List>` | Captured K8s objects |

---

## Valid `checks` Values
| Value | Resources |
|---|---|
| `DEPLOYMENTS` | `apps/v1/Deployment` |
| `CONFIGMAPS` | `v1/ConfigMap` |
| `SERVICES` | `v1/Service` |
| `PVC` | `v1/PersistentVolumeClaim` |
| `IMAGES` | Container images extracted from Deployments |
| `VIRTUALSERVICES` | Istio `VirtualService` |
| `AUTH_POLICY` | Istio `AuthorizationPolicy` |

---

## HTTP Status Code Reference
| Code | Meaning |
|---|---|
| `200` | Success |
| `400` | Bad request / invalid reset token |
| `401` | Unauthenticated / wrong credentials |
| `403` | Forbidden (resource belongs to another user) |
| `404` | Resource not found |
| `409` | Conflict (duplicate baseline) |
| `500` | Server error (K8s connection failure, etc.) |

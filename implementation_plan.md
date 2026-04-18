# KubCompare — Full Auth, Mock Data & API Audit

## Summary
This plan addresses four areas: dummy/mock data, full auth flow (login→JWT→guard), forgot password, and a complete API audit.

---

## 1. Audit Findings

### Backend Auth Issues
| Endpoint | Issue |
|---|---|
| `POST /api/auth/login` | Returns `{ token }` but **does NOT return `userId`**. Frontend needs `userId` to scope history/baselines. |
| `POST /api/auth/verify` | Returns only `{ message: "Verified" }` — used for signup email OTP, not for login. **No JWT issued here.** |
| `POST /api/auth/signup` | Works correctly, sends OTP. |
| `POST /api/auth/forgot-password` | **Does not exist** — needs to be created. |

### Frontend Issues
| Issue | Detail |
|---|---|
| Login doesn't send a token with requests | No `HttpInterceptor` to attach `Authorization: Bearer <token>` header |
| No `AuthGuard` | Protected routes (`/wizard`, `/history`, `/baselines`) are accessible without logging in |
| `verifyOtp()` called after login | The login endpoint already returns a JWT directly — no OTP step needed post-login. OTP verify is only for signup. |
| No Forgot Password page | Missing component + route |

### API Endpoint Mismatch
| Frontend Call | Backend URL | Status |
|---|---|---|
| `POST /api/auth/login` | `POST /api/auth/login` | ✅ Match — but backend doesn't return `userId` |
| `POST /api/auth/verify` | `POST /api/auth/verify` | ✅ Exists — but misused in login flow |
| `POST /api/auth/signup` | `POST /api/auth/signup` | ✅ Match |
| `GET /api/comparison/history/{userId}` | `GET /api/comparison/history/{userId}` | ✅ Match |
| `DELETE /api/comparison/history/{userId}/{id}` | `DELETE /api/comparison/history/{userId}/{id}` | ✅ Match |
| `GET /api/baselines/user/{userId}` | `GET /api/baselines/user/{userId}` | ✅ Match |
| `GET /api/baselines/check?userId=&environmentId=` | `GET /api/baselines/check` | ✅ Match |
| `POST /api/baselines/save` | `POST /api/baselines/save` | ✅ Match |
| `DELETE /api/baselines/{userId}/{id}` | `DELETE /api/baselines/{userId}/{id}` | ✅ Match |
| `POST /api/baselines/compare/{snapshotId}` | `POST /api/baselines/compare/{snapshotId}` | ✅ Match |
| `GET /api/comparison/export/{id}` | `GET /api/comparison/export/{historyId}` | ✅ Match |
| `POST /api/auth/forgot-password` | ❌ Missing | Needs creation |

---

## 2. Changes Required

### Backend

#### [MODIFY] AuthController.java
- Add `userId` to the `login` response body
- Add `POST /api/auth/forgot-password` endpoint

#### [NEW] ForgotPasswordRequest handling in AuthService
- Find user by email, generate reset token, send email (or log for dev)

### Frontend

#### [NEW] `src/app/auth/forgot-password/forgot-password.component.ts/.html`
- Email input form, calls `POST /api/auth/forgot-password`

#### [NEW] `src/app/services/auth.interceptor.ts`
- `HttpInterceptor` that reads `token` from `AuthStorageService` and attaches `Authorization` header

#### [MODIFY] `src/app/app.config.ts`
- Register the interceptor with `withInterceptors()`

#### [NEW] `src/app/guards/auth.guard.ts`
- Checks `AuthStorageService.getToken()` — redirects to `/login` if missing

#### [MODIFY] `src/app/app.routes.ts`
- Apply `authGuard` to the `MainLayoutComponent` route
- Add `/forgot-password` route

#### [MODIFY] `src/app/auth/login/login.component.ts`
- Fix login flow: login → get JWT + userId → store both → navigate to wizard
- Remove the incorrect OTP step from the login flow
- Add "Forgot Password" link

#### [NEW] `src/assets/mock-data.json`
- Dummy history, baselines data for offline testing

#### [NEW] `src/app/services/mock.service.ts`
- `USE_MOCK = true/false` flag
- Returns mock data when flag is on, delegates to `ApiService` when off

#### [MODIFY] `HistoryComponent`, `BaselinesComponent`
- Switch to use `MockService` instead of `ApiService` directly

---

## 3. Verification Plan
- Navigate to `http://localhost:4200` → should redirect to `/login`
- Navigate to `http://localhost:4200/wizard` without login → should redirect to `/login`
- Log in → `Authorization` header visible in browser DevTools on all subsequent requests
- Dummy data loads on History and Baselines pages when `USE_MOCK = true`

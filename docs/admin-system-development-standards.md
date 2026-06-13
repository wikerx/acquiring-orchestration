# Admin System Development Standards

## 1. Scope

This document defines the current admin-system implementation rules for:

- `service-admin`
- shared auth/RBAC code in `component-library/component-db`
- admin frontend `acquiring-frontend/apps/admin-system`
- admin database objects in `payment_acquiring`

Do not apply these rules to cashier, checkout, payment, payout, or merchant-portal without a separate design.

## 2. Frontend Rules

### Layout and style

- Admin frontend changes must preserve the current RuoYi-style layout.
- List pages use:
  - top inline search form
  - operation buttons on the left
  - `RightToolbar` on the right
  - Element Plus table
  - bottom pagination
- Add, edit, detail, and sensitive material views should use one field per row unless the page has an explicit dense-table requirement.
- Form dialogs use `label-width` and one-column field layout for business forms.
- Do not replace the existing i18n, TagsView, settings panel, theme, sidebar, TopNav, footer, or permission directive implementations.

### Internationalization

- All visible page text must be added to:
  - `apps/admin-system/src/i18n/zh-CN.ts`
  - `apps/admin-system/src/i18n/en-US.ts`
- Do not hardcode Chinese or English labels in new business pages except fixed technical identifiers such as `merchantKey`.

### Permission control

- Buttons must use `v-hasPermi`.
- Frontend permission codes must match backend `@RequiresPermission` and database `sys_menu.permission_code`.
- Do not use hardcoded permission bypasses.
- Do not show sensitive OpenAPI key material without a dedicated permission.

### Sensitive key display

- Lists and detail pages may show only key status, algorithm, key size, version, and fingerprint.
- Full key values may be shown only immediately after an authorized generate or rotate operation.
- Full key preview, copy, and download actions must stay behind a sensitive permission such as `merchant:material:view`.
- Platform private keys must not be returned to or displayed by the frontend.

## 3. Backend Rules

### Controller style

- Admin APIs live under `/admin/**`.
- Use `CommonResult.success(...)`.
- Use method-specific mappings:
  - `GET` for detail
  - `POST /search` for paged complex search
  - `POST` for create
  - `PUT` for update/status changes
- Every protected admin endpoint must use `@RequiresPermission`.
- Write operations should use `@OperationLog`.

### DTO rules

- Do not expose database entities directly.
- Use request DTOs for input and response DTOs for output.
- Sensitive values must be separated from normal list/detail DTOs.
- Key summary DTOs should contain metadata only: algorithm, size, enabled status, version, fingerprint, timestamps.

### OpenAPI merchant key rules

- `merchantKey` is the JWT HS256 symmetric signing key. It is expected to be much shorter than RSA keys.
- `platformPublicKeyX509Base64` is the merchant request encryption public key. It is delivered to merchants; merchants use it to encrypt request `data`.
- `platformPrivateKeyPkcs8Base64` is the platform request decryption private key. The platform uses it to decrypt merchant request `data`.
- `merchantResponsePublicKeyX509Base64` is the platform response encryption public key. The platform uses it to encrypt response `data`.
- `merchantResponsePrivateKeyPkcs8Base64` is the merchant response decryption private key. It is delivered to merchants; merchants use it to decrypt platform response `data`.
- Sensitive key viewing, copying, downloading, and updating must use a dedicated permission such as `merchant:key:manage`.
- Generating integration materials can create or rotate keys. Viewing current stored keys must use a separate key-management endpoint so operators can reopen the page later.

## 4. Database Rules

### RBAC

- Admin menu and button authorization tree uses:
  - `sys_menu`
  - `sys_role_menu`
- Runtime API authorization still uses:
  - `sys_permission`
  - `sys_role_permission`
- Do not mix orphan `sys_permission` rows into the role authorization tree.
- `sys_menu.permission_code`, backend `@RequiresPermission`, and frontend `v-hasPermi` must be consistent.

### Admin account model

- `sys_user` is the natural person profile.
- `sys_account` is the login credential under an application.
- `sys_user_role` is retained for user-role compatibility.
- `sys_account_role` is used by the current login and admin user management code.
- Do not drop `sys_account` or `sys_account_role` without replacing the login, session, and user-management code paths first.

### Merchant OpenAPI model

- Merchant master data is stored in `base_merchant_info`.
- JWT keys are stored in `base_merchant_jwt_key`.
- Platform payload RSA keys are stored in the platform payload key table used by the mapper.
- Merchant response public keys are stored in `base_merchant_response_key`.
- Admin pages must not store or log raw secrets outside the intended key tables.

## 5. SQL Change Rules

- SQL scripts must include a short purpose header.
- Destructive scripts must create backup tables first.
- Prefer idempotent SQL with `ON DUPLICATE KEY UPDATE` or guarded `UPDATE`.
- Do not execute `DROP TABLE`, `DROP COLUMN`, or broad `DELETE` until a scan report and rollback plan are reviewed.

## 6. Validation

For admin feature changes, run:

```bash
mvn -pl service-admin -am -DskipTests package
npm run build:admin
```

If SQL is changed, validate menu and role bindings directly against `payment_acquiring` before handing off.

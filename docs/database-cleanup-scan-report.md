# Database Cleanup Scan Report

Scan date: 2026-06-13

Database: `payment_acquiring`

## 1. Summary

This scan checked admin RBAC and merchant-related tables. No destructive database changes were executed.

Important conclusion:

- `sys_account`, `sys_account_role`, `sys_permission`, and `sys_role_permission` are still used by current code.
- They must not be dropped now.
- Cleanup should focus first on orphan rows, duplicated inactive rows, and old backup tables.

## 2. Tables Found

Relevant tables:

```text
base_merchant_info
base_merchant_jwt_key
base_merchant_response_key
sys_account
sys_account_role
sys_app
sys_config
sys_dept
sys_dict_data
sys_dict_type
sys_login_log
sys_login_session
sys_menu
sys_merchant_user
sys_merchant_user_role
sys_notice
sys_oper_log
sys_permission
sys_post
sys_role
sys_role_data_scope
sys_role_menu
sys_role_permission
sys_user
sys_user_role
sys_verify_code
```

## 3. Referential Scan

Current scan counts:

| Check | Count |
| --- | ---: |
| `sys_role_menu` rows with missing menu | 0 |
| `sys_role_menu` rows with missing role | 0 |
| `sys_user_role` rows with missing user | 0 |
| `sys_user_role` rows with missing role | 0 |
| `sys_role_permission` rows with missing role | 0 |
| `sys_role_permission` rows with missing permission | 0 |

No orphan rows were found in these relationship tables.

## 4. Table Responsibility

### Keep

| Table | Reason |
| --- | --- |
| `sys_user` | Natural person profile. |
| `sys_account` | Login credential and account status. Used by login, session, online user, and admin user management. |
| `sys_account_role` | Current login/user-management role binding path. |
| `sys_user_role` | Compatibility role binding. Keep until code is fully migrated. |
| `sys_menu` | Menu and button authorization tree. |
| `sys_role_menu` | Role-to-menu/button authorization. |
| `sys_permission` | Runtime API permission registry. |
| `sys_role_permission` | Runtime API permission binding. |
| `sys_merchant_user`, `sys_merchant_user_role` | Merchant portal compatibility tables. Do not change in admin-only work. |

### Not safe to delete now

| Table / field | Reason |
| --- | --- |
| `sys_account.mobile`, `sys_account.email` | Current admin user management reads/writes these account-level fields. |
| `sys_user.mobile`, `sys_user.email` | Natural person profile fields; may differ by app/account. |
| `sys_permission` | `SystemAuthServiceImpl.check()` still uses API permission matching. |
| `sys_role_permission` | Required by runtime API authorization. |

## 5. Recommended Cleanup Policy

### Safe cleanup candidates

These are safe only after backup and environment confirmation:

```sql
-- Example only. Review before execution.
DELETE rm
FROM sys_role_menu rm
LEFT JOIN sys_menu m ON rm.menu_id = m.id AND m.deleted = 0
WHERE rm.deleted = 0
  AND m.id IS NULL;

DELETE rp
FROM sys_role_permission rp
LEFT JOIN sys_permission p ON rp.permission_id = p.id AND p.deleted = 0
WHERE rp.deleted = 0
  AND p.id IS NULL;
```

Current scan found zero rows for these examples, so no execution is needed now.

### Deferred structural cleanup

Do not run `DROP COLUMN` or `DROP TABLE` until these steps are complete:

1. Choose the final account model: `sys_user` + `sys_account`, or only one table.
2. Migrate code paths in login, online session, user management, and merchant portal.
3. Migrate data and verify row counts.
4. Create rollback SQL.
5. Execute on a copied database first.

## 6. Current Recommendation

Keep the current table structure for now.

The system currently uses:

```text
sys_user
  ↓
sys_account
  ↓
sys_account_role / sys_user_role
  ↓
sys_role
  ↓
sys_role_menu + sys_role_permission
  ↓
sys_menu + sys_permission
```

The duplicate-looking fields are not safe to delete yet because code still distinguishes natural-person data from application login-account data.

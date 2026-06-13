# sys_user vs sys_account 字段职责分析

> 按 admin-system-ruoyi-ai-coding-constraints.md Section 14 要求输出。  
> **不执行 DROP，仅记录职责、使用位置、迁移建议。**

---

## 表职责

| 表 | 职责 |
|----|------|
| `sys_user` | **用户主体** — 代表一个"人"或"实体"，跨系统唯一 |
| `sys_account` | **系统登录账号** — 用户主体在某个系统应用下的登录凭证 |

关系：`sys_user 1 ─── N sys_account`（一个人可以在多个应用中拥有账号）

---

## 重叠字段分析

### `mobile` / `email`

| 字段 | 含义 | 使用场景 |
|------|------|---------|
| `sys_user.mobile` | 用户个人手机号（身份级） | 用户主体资料，如平台用户实名手机号 |
| `sys_account.mobile` | 该系统登录账号的手机号（凭证级） | 发送登录验证码、重置密码 |
| `sys_user.email` | 用户个人邮箱（身份级） | 用户主体资料 |
| `sys_account.email` | 该系统登录账号的邮箱（凭证级） | 发送邮件验证码、账号通知 |

**不是纯重复**：sys_user 存身份信息，sys_account 存凭证/通信渠道。同一个人在不同系统可用不同手机号/邮箱。

当前代码中 `AdminUserController` 读写的主要是 `sys_account` 的字段（loginAccount, mobile, email 作为账号级属性），`sys_user.realName` 作为用户显示名。

### `status`

| 字段 | 含义 |
|------|------|
| `sys_user.status` | 用户主体是否启用（全局级：0=停用，删除用户主体会连带所有账号） |
| `sys_account.status` | 该账号是否启用（应用级：0=停用，可单独禁用某个系统下的账号）|

当前代码中：用户管理页面的启用/停用操作的是 `sys_account.status`。

---

## 当前使用位置

| 字段 | 主要读写位置 |
|------|-------------|
| `sys_user.real_name` | `AdminUserController`、`SystemAuthServiceImpl.toAccountDTO()` |
| `sys_user.mobile` | `AuthRegisterRequest` — 注册时写入 |
| `sys_user.email` | `AuthRegisterRequest` — 注册时写入 |
| `sys_account.login_account` | `AdminUserController`、登录流程 |
| `sys_account.mobile` | `AdminUserController.updateUser()` |
| `sys_account.email` | `AdminUserController.updateUser()` |
| `sys_account.locked` | `SystemAuthServiceImpl` — 登录失败锁定 |
| `sys_account.status` | `AdminUserController.toggleStatus()` |

---

## 迁移建议

### 短期（不破坏现有数据）

1. **明确 API 语义**：`/admin/user` 接口的 `mobile`/`email` 参数应明确操作的是 `sys_account` 还是 `sys_user`
2. **前端表单 label**：如编辑的是账号级信息，label 写"登录手机号"而非"手机号"
3. **不 DROP 任何字段**

### 中期（如需统一）

**方案 A：前端合并展示**
- 用户列表只展示 `sys_account.mobile`/`sys_account.email`（当前已如此）
- `sys_user.mobile`/`sys_user.email` 仅在"用户主体详情"页展示
- 优点：无需 SQL 迁移

**方案 B：统一到 sys_user**
- 将 `sys_account.mobile`/`sys_account.email` 废弃，统一使用 `sys_user.mobile`/`sys_user.email`
- 需要迁移现有数据：`UPDATE sys_user u JOIN sys_account a ON u.id=a.user_id SET u.mobile=COALESCE(u.mobile, a.mobile), u.email=COALESCE(u.email, a.email)`
- 风险：一个用户多账号场景下，可能有不同账号用不同联系方式

### 推荐方案

保持当前分离设计（方案 A），不执行破坏性迁移。
用户主体级信息（姓名、个人手机、邮箱）存在 `sys_user`；
账号凭证级信息（登录账号、登录手机、登录邮箱、锁定状态）存在 `sys_account`。

---

## SQL 迁移草案（仅供后续参考，不执行）

```sql
-- 方案 B：如果需要统一，可参考以下草案
-- 1. 补齐 sys_user 数据
UPDATE sys_user u
INNER JOIN sys_account a ON u.id = a.user_id
SET
    u.mobile = COALESCE(NULLIF(u.mobile, ''), NULLIF(a.mobile, '')),
    u.email = COALESCE(NULLIF(u.email, ''), NULLIF(a.email, ''))
WHERE u.deleted = 0 AND a.deleted = 0;

-- 2. 废弃 sys_account.mobile / sys_account.email（标注为 deprecated，不 DROP）
-- 3. 所有读写统一到 sys_user
-- 4. 前端和 API 适配

-- ⚠️ 注意：此迁移不可逆，需充分验证后再执行
```

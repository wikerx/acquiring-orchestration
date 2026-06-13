# RBAC 角色授权树修复报告

## 1. 根因

角色授权树出现“未归类权限”的根本原因由 **4 个同时存在的问题** 造成：

| # | 问题 | 位置 |
|---|------|------|
| 1 | `sys_permission` 中存在 4 条 `menu_id=0` 的孤儿权限（ID 640-643），无菜单归属 → 前端展示为“未归类权限” | `admin-system-schema.sql:1101-1104` |
| 2 | V3 菜单结构被压缩（部门岗位合并、字典参数合并、日志管理合并），缺少标准独立菜单节点 | `admin-system-schema.sql:714-718` |
| 3 | 部门/岗位菜单（186、187）的 `parent_id=100` 指向已禁用的 V2 菜单 → 成为半孤儿节点 | `admin-system-schema.sql:1085-1088` |
| 4 | 没有独立的“系统监控”顶级目录，在线用户/服务监控/缓存监控在树中无合适位置 | 缺失 |

此外，`*:*:*` 通配符权限被作为普通权限节点展示，需要过滤。

## 2. 修改的后端文件

| 文件 | 修改内容 |
|------|---------|
| `service-admin/.../service/impl/AdminRoleServiceImpl.java` | `rolePermissions()` 方法新增过滤条件：`.gt(menuId, 0L)` 排除孤儿权限 + `.ne(permissionCode, "*:*:*")` 排除通配符 |

**无前端文件修改**（本项目为纯后端，前端为独立项目）。

## 3. 修改的数据库数据

### 3.1 修复脚本（运行于已有数据库）

**文件**: `sql/rbac-permission-tree-repair.sql`

操作内容：
1. **备份** — 创建 `bak_rbac_fix_20260613_*` 备份表
2. **CATALOG 规范化** — 清除所有 CATALOG 菜单的 `permission_code` 和 `component_path`
3. **拆分压缩菜单** — 将 V3 菜单 214（部门岗位）、215（字典参数）、216（日志管理）分别独立为部门管理、字典管理、登录日志
4. **删除孤儿菜单** — 软删除 186（部门管理）、187（岗位管理）及其角色授权（parent_id=100 已失效）
5. **新增独立菜单** — 217（岗位管理）、218（参数设置）、219（操作日志）
6. **新增系统监控目录** — 220（系统监控 CATALOG）、221（在线用户）、222（服务监控）、223（缓存监控）
7. **新增 BUTTON 子菜单** — 为所有 MENU 节点新增 BUTTON 类型子菜单（ID 300-343）
8. **禁用 *:*:*** — 将 `sys_permission` 中 `*:*:*` 的 `status` 设为 0
9. **修复孤儿权限** — 将 `menu_id=0` 的权限 640-643 指向正确的父菜单
10. **重新授权** — 清理旧的 `sys_role_menu` 和 `sys_role_permission`，为 ADMIN_OPERATOR 角色授予全部活跃菜单和权限

### 3.2 种子数据更新

**文件**: `service-admin/src/main/resources/sql/admin-system-schema.sql`

末尾部分修改：
- 删除孤儿菜单 186、187 的 INSERT（parent_id=100 已失效）
- 新增系统监控菜单（220-223）的 INSERT
- 修复权限 632-643 的 `menu_id`：从 186/187/0 改为 214/217/218/215（正确的 V3 菜单 ID）

## 4. 是否废弃 sys_permission / sys_role_permission

**不废弃。** 两张表的用途分开：

| 表 | 用途 | 用于授权树？ |
|---|------|------------|
| `sys_menu` + `sys_role_menu` | 可视化菜单/按钮授权树 | **是** — `/admin/system/roles/menus` 端点 |
| `sys_permission` + `sys_role_permission` | 运行时 API 授权（AntPathMatcher 匹配 `resource_method` + `resource_path`） | **否** |

`SystemAuthServiceImpl.check()` 运行时权限检查依赖 `sys_permission`，因此必须保留。

**关键原则**：角色授权树仅使用 `sys_menu` + `sys_role_menu`。`sys_permission` 不再混入授权树，但保留用于 API 级别鉴权。

## 5. 最终角色授权树数据源

- **数据源**: `sys_menu` 表（仅 `app_id=1`，`status=1`，`deleted=0` 的记录）
- **授权绑定**: `sys_role_menu` 表
- **API 端点**: `POST /admin/system/roles/menus` → `SysRoleMenuAuthDTO`（含树形菜单 + 已选中 ID）
- **菜单授权端点**: `POST /admin/system/roles/menus/grant` → 写入 `sys_role_menu`

## 6. 最终菜单树结构

```
首页 (200, CATALOG)
└── 工作台 (201, MENU, dashboard:view)

系统管理 (210, CATALOG)
├── 用户管理 (211, MENU, system:user:list)
│   ├── 用户查询 (300, BUTTON, system:user:query)
│   ├── 用户新增 (301, BUTTON, system:user:add)
│   ├── 用户修改 (302, BUTTON, system:user:edit)
│   ├── 用户删除 (303, BUTTON, system:user:remove)
│   ├── 用户导出 (304, BUTTON, system:user:export)
│   ├── 重置密码 (305, BUTTON, system:user:resetPwd)
│   ├── 修改状态 (306, BUTTON, system:user:changeStatus)
│   └── 分配角色 (307, BUTTON, system:user:assign-role)
├── 角色管理 (212, MENU, system:role:list)
│   ├── 角色查询 (310, BUTTON)
│   ├── 角色新增 (311, BUTTON)
│   ├── 角色修改 (312, BUTTON)
│   ├── 角色删除 (313, BUTTON)
│   ├── 角色导出 (314, BUTTON)
│   ├── 角色状态 (315, BUTTON)
│   └── 角色授权 (316, BUTTON)
├── 菜单管理 (213, MENU, system:menu:list)
│   ├── 菜单查询/新增/修改/删除 (320-323, BUTTON)
├── 部门管理 (214, MENU, system:dept:list)
│   ├── 部门查询/新增/修改/删除 (324-327, BUTTON)
├── 岗位管理 (217, MENU, system:post:list)
│   ├── 岗位查询/新增/修改/删除/导出 (328-332, BUTTON)
├── 字典管理 (215, MENU, system:dict:list)
│   ├── 字典查询/新增/修改/删除 (333-336, BUTTON)
├── 参数设置 (218, MENU, system:config:list)
│   ├── 参数查询/新增/修改/删除 (337-340, BUTTON)
├── 登录日志 (216, MENU, system:login-log:list)
└── 操作日志 (219, MENU, system:oper-log:list)

系统监控 (220, CATALOG)
├── 在线用户 (221, MENU, system:online:list)
│   └── 强制下线 (341, BUTTON, system:online:forceLogout)
├── 服务监控 (222, MENU, system:server:list)
└── 缓存监控 (223, MENU, system:cache:list)
    ├── 缓存查询 (342, BUTTON, system:cache:query)
    └── 缓存清理 (343, BUTTON, system:cache:clear)

商户管理 / 基础数据 / 权限中心 / 安全中心 ...（保持不变）
```

## 7. 测试结果

- [x] `POST /admin/system/roles/menus` — 树结构清晰，无“未归类权限”
- [x] `POST /admin/system/roles/permissions` — 已过滤（无 `*:*:*`，无 `menu_id=0`）
- [x] 系统管理下正确展示：用户/角色/菜单/部门/岗位/字典/参数/登录日志/操作日志
- [x] 系统监控独立展示：在线用户/服务监控/缓存监控
- [x] BUTTON 子节点正确挂载在对应菜单下
- [x] admin 角色拥有全部菜单和按钮权限
- [x] 编译通过

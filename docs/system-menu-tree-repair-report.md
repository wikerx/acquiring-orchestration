# 系统菜单树层级修复报告

## 根因

导入 `sql/system-base-menu-repair.sql` 后层级异常，主要原因不是前端路由解析，而是数据库菜单数据同时存在多套初始化版本：

- 旧菜单 `id = 1..184` 仍处于启用状态或仍有关联授权，菜单管理树会继续展示。
- `AdminMenuServiceImpl.treeMenus` 默认只过滤 `status=1`，不会过滤 `visible=0`，所以仅设置 `visible=0` 的旧菜单仍会出现在菜单管理树。
- 系统监控目录和子菜单未在上一版脚本中统一校正。
- 部分按钮权限来自历史标准或前端占位，但后端没有对应接口，继续放入授权树会造成“有权限但功能不可用”。

## 修复策略

新增脚本：

```text
sql/system-menu-tree-direct-db-fix.sql
```

该脚本不直接删除大量数据，采用以下方式修复：

- 禁用历史冲突目录：`安全中心`、旧 `系统管理`、旧 `系统监控` 等重复目录。
- 软失效旧菜单和异常菜单的 `sys_role_menu` 绑定。
- 校正系统管理目录：
  - 用户管理
  - 角色管理
  - 菜单管理
  - 部门管理
  - 岗位管理
  - 字典管理
  - 参数设置
  - 通知公告
  - 日志管理
- 校正系统监控目录：
  - 在线用户
  - 服务监控
  - 缓存监控
- 校正基础数据目录：
  - 国家/地区
  - 币种管理
  - 地区币种配置
- 按钮权限只挂到对应菜单下，且 `visible=0,status=1`。
- 当前没有后端接口的按钮权限设为 `status=0`，避免进入授权树。
- 补齐 admin 角色对有效目录、菜单、按钮的绑定。

## 前端路由匹配

当前动态路由按后端 `menu_type = MENU` 且有 `route_path` 的节点生成，组件路径可匹配：

```text
/system/user     -> system/user/index
/system/role     -> system/role/index
/system/menu     -> system/menu/index
/system/dept     -> system/dept/index
/system/post     -> system/post/index
/system/dict     -> system/dict/index
/system/config   -> system/config/index
/system/notice   -> system/notice/index
/system/log      -> system/log/index
/monitor/online  -> monitor/online/index
/monitor/server  -> monitor/server/index
/monitor/cache   -> monitor/cache/index
/base/country    -> base/country
/base/currency   -> base/currency
/base/region-currency -> base/region-currency
```

按钮节点 `menu_type=BUTTON, visible=0` 不会进入侧边栏和动态路由。

## 权限一致性

保留并授权的按钮权限均有后端接口或前端实际调用：

```text
system:user:add
system:user:edit
system:user:changeStatus
system:user:resetPwd
system:user:assign-role
system:role:add
system:role:edit
system:role:remove
system:role:changeStatus
system:role:dataScope
system:menu:add
system:menu:edit
system:dept:query
system:dept:add
system:dept:edit
system:dept:remove
system:dept:export
system:post:query
system:post:add
system:post:edit
system:post:remove
system:post:export
system:online:forceLogout
system:cache:query
system:cache:clear
system:dict:list
system:dict:add
system:dict:edit
system:dict:remove
system:dictData:list
system:dictData:add
system:dictData:edit
system:dictData:remove
system:config:list
system:config:add
system:config:edit
system:config:remove
system:config:refresh
system:config:export
system:notice:add
system:notice:edit
system:notice:remove
system:oper-log:list
base:country:list
base:country:add
base:country:edit
base:country:remove
base:country:export
base:country:changeStatus
base:currency:list
base:currency:add
base:currency:edit
base:currency:remove
base:currency:export
base:currency:changeStatus
base:countryCurrency:list
base:countryCurrency:add
base:countryCurrency:edit
base:countryCurrency:remove
base:countryCurrency:export
base:countryCurrency:changeStatus
```

禁用的占位或无接口按钮：

```text
system:user:query
system:user:remove
system:user:export
system:role:query
system:role:export
system:menu:query
system:menu:remove
```

## 执行建议

当前本地数据库已按 `sql/system-menu-tree-direct-db-fix.sql` 的目标状态完成直接修复。

如需在其他环境重放，先确认目标库菜单 ID 与脚本顶部“真实 ID”一致，再执行完整脚本。

## 当前本地库验收结果

2026-06-13 已通过 JDBC 直连 `payment_acquiring` 只读验证：

```text
orphan_active_count = 0
role1_visible_menu_count = 21
role1_active_button_count = 64
```

admin 当前可见一级目录和核心子菜单：

```text
安全中心
├── 会话管理
└── 密钥与API安全

系统管理
├── 用户管理
├── 角色管理
├── 菜单管理
├── 部门管理
├── 岗位管理
├── 字典管理
├── 参数设置
├── 通知公告
└── 日志管理

系统监控
├── 在线用户
├── 服务监控
└── 缓存监控

基础数据
├── 国家/地区
├── 币种管理
└── 地区币种配置
```

执行后验证：

- 菜单管理树中系统管理保留用户、角色、菜单、部门、岗位、字典、参数、通知、日志。
- 系统监控作为一级目录，下面只保留 3 个子菜单。
- 基础数据作为一级目录，下面保留国家/地区、币种管理、地区币种配置。
- 按钮权限只在角色授权树中展示，不进入侧边栏。
- 刷新前端后动态路由不出现 MissingView。
- admin 角色拥有有效菜单和按钮权限。

## 本次未做

- 未修改业务代码。
- 未物理删除大量历史数据。
- 未处理商户端、收银台、OpenAPI、支付、出款等业务模块。

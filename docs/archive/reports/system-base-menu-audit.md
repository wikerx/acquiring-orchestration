# 系统管理与基础数据菜单专项审计报告

## 范围

- 系统管理：部门管理、岗位管理、字典管理、参数设置。
- 基础数据：国家/地区、币种管理、地区币种配置。
- 前端项目：`/Users/scott/Documents/code/ideaCodex/acquiring-frontend/apps/admin-system`。
- 后端项目：`/Users/scott/Documents/code/ideaCodex/acquiring-orchestration/service-admin` 及关联 `component-db`。
- SQL：`service-admin/src/main/resources/sql`、`sql`。

## 扫描结论

1. 七个目标页面均存在。
2. 字典管理当前页面主要覆盖字典类型，字典数据接口与权限已存在，前端完整维护视图仍是后续优化项。
3. 参数设置导出、刷新缓存权限入口已存在。
4. 国家/地区、币种管理、地区币种配置按钮权限已绑定。
5. 后端目标 Controller 已使用 `@RequiresPermission`，核心权限标识已统一。
6. SQL 初始化脚本存在多代菜单数据残留，已通过真实 ID 修复脚本统一。
7. 本地没有 `mysql` CLI，但已通过 JDBC 直连 `payment_acquiring` 完成真实数据库核验。

## 主要问题

- `system:dict:delete` 应统一为 `system:dict:remove`。
- `system:config:delete` 应统一为 `system:config:remove`。
- `base:region-currency:*` 应统一为 `base:countryCurrency:*`。
- `base:country:delete`、`base:currency:delete` 应统一为 `remove`。
- 旧 `sql/system-base-menu-repair.sql` 使用错误菜单 ID，已显式废弃，避免再次导入污染层级。
- `system:dictData:*` 权限菜单已在当前库存在并绑定 admin。
- `system:dept:export`、`system:dict:export`、`system:dict:refresh`、`system:config:export`、`system:config:refresh` 等按钮权限已在当前库存在并绑定 admin。
- 基础数据按钮权限已写入 `sys_menu` 并绑定 admin。

## 建议

不要再执行 `sql/system-base-menu-repair.sql`。该文件已废弃并会主动中止。

如需重放修复，使用 `sql/system-menu-tree-direct-db-fix.sql`，并先确认目标库菜单 ID 与脚本顶部“真实 ID”一致。

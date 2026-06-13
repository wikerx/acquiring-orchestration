# 系统管理与基础数据菜单修复报告

## 修复内容

本次修复按标准 RBAC 权限标识补齐系统管理与基础数据相关菜单、按钮、接口和前端权限绑定。

## 后端修改

- `AdminDeptController`：详情权限改为 `system:dept:query`，新增 `system:dept:export` 导出接口。
- `AdminPostController`：详情权限改为 `system:post:query`，新增 `system:post:export` 导出接口。
- `AdminDictController`：拆分字典类型与字典数据权限，补齐 `add/edit/remove/export/refresh` 与 `dictData:*` 接口。
- `AdminConfigController`：拆分新增/编辑/详情/删除权限，补齐导出和刷新缓存接口。
- `AdminBaseCountryController`：补齐导出和状态切换接口。
- `AdminBaseCurrencyController`：补齐导出和状态切换接口。
- `AdminBaseRegionCurrencyController`：权限统一为 `base:countryCurrency:*`，补齐详情、新增、删除、导出、状态切换接口。

## 前端修改

- `api/system/dept.ts`、`api/system/post.ts`：补齐导出 API。
- `api/system/dict.ts`：补齐字典类型、字典数据、导出、刷新缓存 API。
- `api/system/config.ts`：补齐更新、导出、刷新缓存 API。
- `api/base/country.ts`、`api/base/currency.ts`：补齐状态切换和导出 API。
- `api/base/regionCurrency.ts`：补齐新增、删除、状态切换和导出 API。
- 目标页面补齐 `v-hasPermi`，避免按钮裸露。
- `views/system/dept/index.vue`、`views/system/post/index.vue`、`views/system/dict/index.vue`、`views/system/config/index.vue`：状态开关补齐对应编辑权限控制。
- `views/base/region-currency/index.vue`：补齐地区币种新增、切换币种、删除、状态切换、导出入口。
- `constants/adminModules.ts`：地区币种菜单权限统一为 `base:countryCurrency:list`。

## SQL 修改

- `sql/system-base-menu-repair.sql` 已废弃并主动中止执行，原因是早期脚本使用错误菜单 ID。
- 新增 `sql/system-menu-tree-direct-db-fix.sql`，按当前真实菜单 ID 修复系统管理、系统监控、基础数据及 admin 授权。
- 修正 `admin-system-schema.sql` 中目标模块的非标准权限标识。
- 修复 admin 角色对目标菜单和按钮的授权。

## 新增/统一权限

- `system:dept:export`
- `system:dict:export`
- `system:dict:refresh`
- `system:dictData:list/query/add/edit/remove/export`
- `system:config:export`
- `system:config:refresh`
- `base:country:changeStatus`
- `base:currency:changeStatus`
- `base:countryCurrency:list/query/add/edit/remove/export/changeStatus`

## 验证结果

- 后端：`mvn -pl service-admin -am -DskipTests package` 通过。
- 前端：`npm run build:admin` 通过。
- 数据库：已通过 JDBC 直连本地 `payment_acquiring` 验证，`orphan_active_count=0`，`role1_visible_menu_count=21`，`role1_active_button_count=64`。

## 遗留风险

- 字典数据前端仍需要进一步做成完整维护视图，目前后端接口和权限已补齐。
- 部门、岗位和基础数据 Controller 仍直连 Mapper，后续可按 Service 层标准拆分。
- 导出接口当前返回数据列表，不是文件流下载；如需要 Excel/CSV 文件，应统一封装下载工具。
- 旧 `apps/admin-system/src/api/dictApi.ts` 当前未被页面引用，仍保留为历史兼容文件，后续可按前端 API 目录整理统一删除。

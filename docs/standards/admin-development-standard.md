# 管理后台开发规范

## 1. 适用范围

本规范适用于以下范围：

- `service-admin`
- `component-library/component-db` 中与后台认证、RBAC 相关的共享能力
- 前端 `acquiring-frontend/apps/admin-system`
- 数据库 `payment_acquiring` 中与后台管理相关的对象

如无单独设计说明，不要把本规范直接套用到 `checkout`、`payment`、`payout` 或 `merchant-portal`。

## 2. 前端规范

### 2.1 页面布局

- 后台前端应保持当前 RuoYi 风格布局，不要随意替换整体框架。
- 列表页默认使用顶部行内查询区、左侧操作按钮、右侧 `RightToolbar`、中间 `Element Plus` 表格和底部分页。
- 新增、编辑、详情、敏感资料展示页面默认一行一项，除非该页面本身就是密集型表单或表格。
- 表单弹窗统一设置 `label-width`，业务表单优先单列布局。
- 不要替换现有 i18n、`TagsView`、设置面板、主题、侧边栏、`TopNav`、页脚和权限指令实现。

### 2.2 国际化

- 所有可见文案都要同步维护到 `apps/admin-system/src/i18n/zh-CN.ts` 和 `apps/admin-system/src/i18n/en-US.ts`。
- 除 `merchantKey` 这类技术标识外，不要在页面中硬编码中文或英文文案。

### 2.3 权限控制

- 按钮必须使用 `v-hasPermi`。
- 前端权限码必须与后端 `@RequiresPermission` 及数据库 `sys_menu.permission_code` 保持一致。
- 不允许写死权限绕过逻辑。
- 涉及 OpenAPI 密钥材料的页面，必须使用独立敏感权限控制。

### 2.4 敏感密钥展示

- 列表页和详情页只允许展示状态、算法、长度、版本、指纹等摘要信息。
- 完整密钥只允许在授权的生成或轮换完成后短时展示。
- 查看、复制、下载完整密钥必须挂在独立敏感权限之下，例如 `merchant:material:view`。
- 平台私钥不得返回给前端，也不得在页面明文展示。

### 2.5 时间展示

- Admin 页面日期时间统一展示为 `yyyy-MM-dd HH:mm:ss`，不展示毫秒。
- 表格列、详情页、弹窗中的时间字段优先使用 `BaseDateTime` 或项目统一 `formatDateTime`。
- 不要在页面内重复实现时间格式化函数，不要用 `substring` 截断接口返回时间。
- 日期范围查询组件保持原有入参与交互，不因展示格式治理改变查询语义。

### 2.6 导出下载

- 管理端列表导出统一使用 `apps/admin-system/src/utils/download.ts` 中的 `downloadExcel`。
- 只有密钥、证书、压缩包、导入模板等普通二进制文件允许使用 `downloadBlob`。
- 前端导出接口不要手写文件名兜底规则，优先使用后端 `Content-Disposition` 返回的文件名。
- 新增页面的导出按钮文案、成功提示和权限码必须走现有 i18n 与 `v-hasPermi` 机制。

## 3. 后端规范

### 3.1 Controller 风格

- 后台接口统一挂在 `/admin/**`。
- 统一使用 `CommonResult.success(...)` 返回结果。
- 详情查询优先使用 `GET`，复杂分页查询优先使用 `POST /search`，新增使用 `POST`，编辑或状态变更使用 `PUT`。
- 所有受保护接口必须显式使用 `@RequiresPermission`。
- 写操作应配合 `@OperationLog` 记录审计信息。

### 3.2 DTO 规则

- 不允许直接暴露数据库实体。
- 输入使用请求 DTO，输出使用响应 DTO。
- 敏感数据与普通列表/详情 DTO 分离。
- 密钥摘要 DTO 只保留算法、长度、启用状态、版本、指纹、时间戳等元数据。

### 3.3 OpenAPI 密钥材料规则

- `merchantKey` 是 JWT HS256 对称签名密钥，长度明显短于 RSA 密钥属于正常设计。
- `platformPublicKeyX509Base64` 是商户请求加密使用的平台公钥，用于加密请求 `data`。
- `platformPrivateKeyPkcs8Base64` 是平台请求解密私钥，仅平台内部使用。
- `merchantResponsePublicKeyX509Base64` 是平台响应加密时使用的商户响应公钥。
- `merchantResponsePrivateKeyPkcs8Base64` 是商户侧解密平台响应 `data` 的私钥，只能交付商户服务端保存。
- 查看、复制、下载、更新密钥必须使用独立权限，例如 `merchant:key:manage`。
- 生成接入材料与查看当前已存密钥应拆成不同接口，避免页面刷新后无法重新进入受控查看流程。

### 3.4 Excel 导出

- 管理系统列表导出统一使用 `component-library/component-excel` 的 `ExcelExportService`。
- 固定列导出使用 `ExcelExportRequest` 和 `@ExcelExportColumn`；动态列导出使用 `ExcelDynamicExportRequest`。
- 禁止业务接口自行拼装 Excel、CSV 响应头、文件名和表头样式；导入模板等非列表导出场景除外。
- 导出文件名统一为 `{导出标题}_{yyyyMMddHHmmss}.xlsx`，导出标题必须来自 Excel 国际化文案或当前功能名称。
- Excel 内部统一包含标题行、导出元信息行、表头行和数据行；导出时间展示为 `yyyy-MM-dd HH:mm:ss`。
- 表头、标题、状态、风险等级、决策动作、有效期等展示值必须国际化，不能导出数据库原始值或 `0/1` 这类难以理解的编码。
- 查询条件摘要应尽量反映当前筛选条件；无筛选条件时使用 `excel.common.noCondition`。

## 4. 数据库规范

### 4.1 RBAC

- 后台菜单与按钮授权树使用 `sys_menu` 和 `sys_role_menu`。
- 运行时 API 鉴权继续依赖 `sys_permission` 和 `sys_role_permission`。
- 不要把孤儿 `sys_permission` 记录混入角色授权树。
- `sys_menu.permission_code`、后端 `@RequiresPermission`、前端 `v-hasPermi` 必须一致。

### 4.2 后台账号模型

- `sys_user` 表示自然人主体。
- `sys_account` 表示某个应用下的登录账号。
- `sys_user_role` 保留为兼容角色关系。
- `sys_account_role` 是当前登录与后台用户管理真实使用的角色关系。
- 在登录、会话、用户管理代码未完成迁移前，不要删除 `sys_account` 或 `sys_account_role`。

### 4.3 商户 OpenAPI 模型

- 商户主数据存放在 `base_merchant_info`。
- JWT 密钥存放在 `base_merchant_jwt_key`。
- 平台请求体 RSA 密钥存放在对应平台密钥表。
- 商户响应公钥存放在 `base_merchant_response_key`。
- 后台页面不得在这些目标表之外另存原始密钥，也不得写入日志。

## 5. SQL 变更规范

- SQL 脚本必须带简短用途说明。
- 破坏性脚本必须先准备备份表。
- 优先使用带保护条件的幂等 SQL，例如 `ON DUPLICATE KEY UPDATE` 或条件 `UPDATE`。
- 在扫描报告和回滚方案审阅完成前，不要执行 `DROP TABLE`、`DROP COLUMN` 或大范围 `DELETE`。
- 表示具体时间点的字段统一使用 `DATETIME(3)`，默认当前时间使用 `CURRENT_TIMESTAMP(3)`，自动更新时间使用 `ON UPDATE CURRENT_TIMESTAMP(3)`。
- `DATE`、`TIME`、只表示业务日期的字段、外部渠道原始字符串时间字段不要强行改为 `DATETIME(3)`。
- 已有表需要统一时间精度时，只生成迁移 SQL 草案并人工确认，不直接执行数据库变更。

## 6. 验证要求

后台功能调整后，至少应执行：

```bash
mvn -pl service-admin -am -DskipTests package
npm run build:admin
```

如涉及 SQL 变更，应在交付前直接核对 `payment_acquiring` 中的菜单和角色绑定结果。

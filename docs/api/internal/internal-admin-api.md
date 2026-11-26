# 内部管理系统接口文档

## 1. 文档说明

### 1.1 文档定位

本文档用于支付框架内部管理系统前端对接，覆盖后台管理服务 `service-admin` 当前提供的内部接口。

后续所有内部管理接口统一在本文档中维护，避免接口散落在不同文档中造成前后端理解不一致。

### 1.2 适用范围

本文档适用于以下系统或角色：

| 角色 | 说明 |
| --- | --- |
| 管理后台前端 | 对接系统配置、数据字典、操作日志等内部接口 |
| 管理后台后端 | 维护内部接口、字段、错误码和兼容性说明 |
| 测试人员 | 编写接口测试用例、核对请求和响应格式 |

### 1.3 服务说明

| 项目 | 内容 |
| --- | --- |
| 服务名称 | `service-admin` |
| 接口前缀 | `/admin` |
| 数据格式 | `application/json;charset=UTF-8` |
| 字符集 | `UTF-8` |
| 适用环境 | `dev`、`test`、`uat`、`prod` |

### 1.4 参数必填标识

| 标识 | 说明 |
| --- | --- |
| M | 必填 |
| O | 可选 |
| C | 条件必填 |

### 1.5 访问入口与公共请求头

管理后台前端本地开发时通过 `service-gateway` 访问 `service-admin`：

| 项目 | 内容 |
| --- | --- |
| 前端开发地址 | `http://localhost:5173` |
| 网关地址 | `http://127.0.0.1:8000` |
| 后端服务地址 | `http://127.0.0.1:8001` |
| 前端代理 | `/admin/** -> http://127.0.0.1:8000/admin/**` |

除 `/admin/auth/login` 和 `/admin/health/**` 外，后台接口已接入登录态和权限拦截，前端必须在请求头中携带登录返回的 token。

| 参数 | 位置 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| Content-Type | Header | M | `application/json;charset=UTF-8` | 请求体格式 |
| Authorization | Header | C | `Bearer {accessToken}` | 登录后必填；登录和健康检查接口不需要 |
| X-Request-Id | Header | O | `REQ202606060001` | 前端生成的请求ID，便于日志排查 |
| X-Trace-Id | Header | O | `TRACE202606060001` | 链路追踪ID |
| X-Operator-Id | Header | O | `10001` | 当前后台操作人ID，操作日志自动采集时使用 |
| X-Operator-Name | Header | O | `admin` | 当前后台操作人名称，操作日志自动采集时使用 |
| X-Operator-Type | Header | O | `1` | 操作人类别：1后台用户，2商户用户，3系统任务 |
| X-Merchant-Id | Header | O | `200045` | 当前操作涉及商户时传入，操作日志自动采集时使用 |

### 1.6 公共响应结构

除健康检查接口外，管理内部接口统一返回 `CommonResult<T>`：

```json
{
  "code": "T200",
  "message": "Success",
  "data": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | string | M | 响应码，`T200` 表示成功 |
| message | string | M | 响应说明 |
| data | object/array/null | O | 响应数据 |

### 1.7 公共分页结构

列表接口统一支持分页，查询请求可携带 `pageNo` 和 `pageSize`。不传时默认查询第 1 页，每页 20 条；服务端单页最大限制为 500 条。

#### 1.7.1 分页请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| pageNo | integer | O | `1` | 当前页码，从 1 开始 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 1.7.2 分页响应 data 结构

```json
{
  "total": 100,
  "pageNo": 1,
  "pageSize": 20,
  "pages": 5,
  "records": []
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| total | long | M | 满足查询条件的总记录数 |
| pageNo | long | M | 当前页码 |
| pageSize | long | M | 每页记录数 |
| pages | long | M | 总页数 |
| records | array | M | 当前页列表数据 |

健康检查接口当前返回 `ApiResult<T>`，成功结构如下：

```json
{
  "code": "0",
  "message": "success",
  "data": "service-admin"
}
```

### 1.8 通用枚举

#### 1.8.1 启用状态

| 值 | 说明 |
| --- | --- |
| 0 | 停用 |
| 1 | 启用 |

#### 1.8.2 是否标识

| 值 | 说明 |
| --- | --- |
| 0 | 否 |
| 1 | 是 |

#### 1.8.3 配置值类型

| 值 | 说明 |
| --- | --- |
| 1 | 字符串 |
| 2 | 数字 |
| 3 | 布尔 |
| 4 | JSON |

#### 1.8.4 操作日志业务类型

| 值 | 说明 |
| --- | --- |
| 1 | 新增 |
| 2 | 修改 |
| 3 | 删除 |
| 4 | 查询 |
| 5 | 导出 |
| 6 | 审核 |
| 7 | 冻结 |
| 8 | 解冻 |

#### 1.8.5 操作人类别

| 值 | 说明 |
| --- | --- |
| 1 | 后台用户 |
| 2 | 商户用户 |
| 3 | 系统任务 |

### 1.9 安全注意事项

1. 内部接口只能用于管理后台、内部服务或受控网络环境。
2. 操作日志接口的 `requestParam`、`responseResult` 必须由调用方先脱敏，或由统一操作日志 AOP 自动完成脱敏后再入 MQ。
3. 禁止在操作日志中记录卡号、CVV、JWT、token、merchantKey、私钥、密码等敏感明文。
4. `sys_config` 不建议保存密钥、数据库密码、私钥等高敏感配置；这类数据应走 KMS/HSM 或专门密钥表。

### 1.10 本地初始化管理员

`service-admin/src/main/resources/sql/admin-system-schema.sql` 会初始化一个本地开发管理员账号。

| 项目 | 内容 |
| --- | --- |
| 登录账号 | `admin` |
| 初始密码 | `Admin@123456` |
| 默认角色 | `ADMIN_OPERATOR` |
| 适用范围 | 本地开发、首次初始化验证 |

安全要求：

1. 初始密码只用于本地开发和首次初始化验证，部署到共享环境后必须立即修改。
2. 不要在生产环境继续使用该账号密码。
3. 登录后前端会从 `/admin/auth/me` 获取当前账号菜单和权限，菜单展示与路由访问均以 `service-admin` 返回的 RBAC 数据为准。

## 2. 接口清单

| 模块 | 功能 | 方法 | 地址 |
| --- | --- | --- | --- |
| 健康检查 | 服务存活探测 | GET | `/admin/health` |
| 登录权限 | 管理后台登录 | POST | `/admin/auth/login` |
| 登录权限 | 查询当前账号、菜单和权限 | GET | `/admin/auth/me` |
| 登录权限 | 退出登录 | POST | `/admin/auth/logout` |
| 登录权限 | 注册后台账号 | POST | `/admin/auth/register` |
| 用户管理 | 查询后台用户列表 | POST | `/admin/system/users/search` |
| 角色管理 | 查询后台角色列表 | POST | `/admin/system/roles/search` |
| 菜单管理 | 查询后台菜单树 | POST | `/admin/system/menus/tree` |
| 系统配置 | 保存或更新配置 | POST | `/admin/system/configs` |
| 系统配置 | 根据配置键查询配置 | GET | `/admin/system/configs/{configKey}` |
| 系统配置 | 查询配置列表 | POST | `/admin/system/configs/search` |
| 系统配置 | 删除配置 | DELETE | `/admin/system/configs/{configKey}` |
| 字典类型 | 保存或更新字典类型 | POST | `/admin/system/dicts/types` |
| 字典类型 | 查询字典类型列表 | POST | `/admin/system/dicts/types/search` |
| 字典类型 | 删除字典类型 | DELETE | `/admin/system/dicts/types/{dictType}` |
| 字典数据 | 保存或更新字典数据 | POST | `/admin/system/dicts/data` |
| 字典数据 | 查询字典数据列表 | POST | `/admin/system/dicts/data/search` |
| 字典数据 | 删除字典数据 | DELETE | `/admin/system/dicts/data/{dictType}/{dictValue}` |
| 登录日志 | 查询登录日志列表 | POST | `/admin/system/login-logs/search` |
| 操作日志 | 写入操作日志（内部兼容入口） | POST | `/admin/system/oper-logs` |
| 操作日志 | 查询操作日志列表 | POST | `/admin/system/oper-logs/search` |
| 系统监控 | 查询数据源监控快照 | GET | `/admin/monitor/datasource` |

## 3. 健康检查

### 3.1 服务健康检查

#### 3.1.1 接口说明

用于网关、注册中心、部署平台或前端开发环境确认 `service-admin` 是否正常启动。

#### 3.1.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | GET |
| 请求地址 | `/admin/health` |
| 请求体 | 无 |

#### 3.1.3 响应示例

```json
{
  "code": "0",
  "message": "success",
  "data": "service-admin"
}
```

## 4. 系统参数配置接口

### 4.1 保存或更新系统参数配置

#### 4.1.1 接口说明

根据 `configKey` 保存或更新系统参数配置。若配置键不存在则新增，若存在则更新。

#### 4.1.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/configs` |
| Content-Type | `application/json;charset=UTF-8` |

#### 4.1.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| configName | string | M | `系统初始化密码` | 参数名称 |
| configKey | string | M | `sys.user.init_password` | 参数键名，全局唯一 |
| configValue | string | O | `123456` | 参数值 |
| valueType | integer | M | `1` | 值类型：1字符串，2数字，3布尔，4JSON |
| configGroup | string | O | `system` | 配置分组 |
| systemBuiltin | integer | O | `1` | 是否系统内置：0否，1是 |
| visible | integer | O | `1` | 是否前端可见：0否，1是 |
| encrypted | integer | O | `0` | 是否加密存储：0否，1是 |
| status | integer | O | `1` | 状态：0停用，1启用 |
| remark | string | O | `后台用户初始化密码` | 备注 |
| operator | string | O | `admin` | 当前操作人 |

#### 4.1.4 请求示例

```json
{
  "configName": "系统初始化密码",
  "configKey": "sys.user.init_password",
  "configValue": "123456",
  "valueType": 1,
  "configGroup": "system",
  "systemBuiltin": 1,
  "visible": 0,
  "encrypted": 1,
  "status": 1,
  "remark": "后台用户初始化密码",
  "operator": "admin"
}
```

#### 4.1.5 响应参数

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键ID |
| configName | string | 参数名称 |
| configKey | string | 参数键名 |
| configValue | string | 参数值 |
| valueType | integer | 值类型 |
| configGroup | string | 配置分组 |
| systemBuiltin | integer | 是否系统内置 |
| visible | integer | 是否前端可见 |
| encrypted | integer | 是否加密存储 |
| status | integer | 状态 |
| remark | string | 备注 |
| createdBy | string | 创建人 |
| updatedBy | string | 更新人 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

#### 4.1.6 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "id": 1,
    "configName": "系统初始化密码",
    "configKey": "sys.user.init_password",
    "configValue": "123456",
    "valueType": 1,
    "configGroup": "system",
    "systemBuiltin": 1,
    "visible": 0,
    "encrypted": 1,
    "status": 1,
    "remark": "后台用户初始化密码",
    "createdBy": "admin",
    "updatedBy": "admin",
    "createdAt": "2026-06-06T10:00:00",
    "updatedAt": "2026-06-06T10:00:00"
  }
}
```

### 4.2 根据配置键查询系统参数配置

#### 4.2.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | GET |
| 请求地址 | `/admin/system/configs/{configKey}` |

#### 4.2.2 路径参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| configKey | string | M | `sys.user.init_password` | 参数键名 |

#### 4.2.3 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "id": 1,
    "configName": "系统初始化密码",
    "configKey": "sys.user.init_password",
    "configValue": "123456",
    "valueType": 1,
    "configGroup": "system",
    "systemBuiltin": 1,
    "visible": 0,
    "encrypted": 1,
    "status": 1,
    "remark": "后台用户初始化密码",
    "createdBy": "admin",
    "updatedBy": "admin",
    "createdAt": "2026-06-06T10:00:00",
    "updatedAt": "2026-06-06T10:00:00"
  }
}
```

### 4.3 查询系统参数配置列表

#### 4.3.1 接口说明

按条件查询系统参数配置列表。不传请求体或传 `{}` 表示查询全部未删除配置。

#### 4.3.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/configs/search` |

#### 4.3.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| configName | string | O | `系统` | 参数名称，右模糊查询 |
| configKey | string | O | `sys.user.init_password` | 参数键名，精确查询 |
| configGroup | string | O | `system` | 配置分组，精确查询 |
| status | integer | O | `1` | 状态：0停用，1启用 |
| pageNo | integer | O | `1` | 当前页码 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 4.3.4 请求示例

```json
{
  "configGroup": "system",
  "status": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

#### 4.3.5 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "total": 1,
    "pageNo": 1,
    "pageSize": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "configName": "系统初始化密码",
        "configKey": "sys.user.init_password",
        "configValue": "123456",
        "valueType": 1,
        "configGroup": "system",
        "systemBuiltin": 1,
        "visible": 0,
        "encrypted": 1,
        "status": 1,
        "remark": "后台用户初始化密码",
        "createdBy": "admin",
        "updatedBy": "admin",
        "createdAt": "2026-06-06T10:00:00",
        "updatedAt": "2026-06-06T10:00:00"
      }
    ]
  }
}
```

### 4.4 删除系统参数配置

#### 4.4.1 接口说明

根据 `configKey` 软删除系统参数配置。删除后不会物理删除记录，会将 `deleted` 更新为当前记录 ID。

#### 4.4.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | DELETE |
| 请求地址 | `/admin/system/configs/{configKey}` |

#### 4.4.3 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": null
}
```

## 5. 数据字典接口

### 5.1 保存或更新字典类型

#### 5.1.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/dicts/types` |

#### 5.1.2 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| dictName | string | M | `商户状态` | 字典名称 |
| dictType | string | M | `merchant_status` | 字典类型编码 |
| bizDomain | string | O | `merchant` | 业务域 |
| systemBuiltin | integer | O | `1` | 是否系统内置 |
| editable | integer | O | `1` | 是否允许编辑 |
| status | integer | O | `1` | 状态 |
| remark | string | O | `商户基础状态` | 备注 |
| operator | string | O | `admin` | 当前操作人 |

#### 5.1.3 请求示例

```json
{
  "dictName": "商户状态",
  "dictType": "merchant_status",
  "bizDomain": "merchant",
  "systemBuiltin": 1,
  "editable": 1,
  "status": 1,
  "remark": "商户基础状态",
  "operator": "admin"
}
```

#### 5.1.4 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "id": 1,
    "dictName": "商户状态",
    "dictType": "merchant_status",
    "bizDomain": "merchant",
    "systemBuiltin": 1,
    "editable": 1,
    "status": 1,
    "remark": "商户基础状态",
    "createdAt": "2026-06-06T10:00:00",
    "updatedAt": "2026-06-06T10:00:00"
  }
}
```

### 5.2 查询字典类型列表

#### 5.2.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/dicts/types/search` |

#### 5.2.2 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| dictName | string | O | `商户` | 字典名称，右模糊查询 |
| dictType | string | O | `merchant_status` | 字典类型编码，精确查询 |
| bizDomain | string | O | `merchant` | 业务域，精确查询 |
| status | integer | O | `1` | 状态 |
| pageNo | integer | O | `1` | 当前页码 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 5.2.3 请求示例

```json
{
  "bizDomain": "merchant",
  "status": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

#### 5.2.4 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "total": 1,
    "pageNo": 1,
    "pageSize": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "dictName": "商户状态",
        "dictType": "merchant_status",
        "bizDomain": "merchant",
        "systemBuiltin": 1,
        "editable": 1,
        "status": 1,
        "remark": "商户基础状态",
        "createdAt": "2026-06-06T10:00:00",
        "updatedAt": "2026-06-06T10:00:00"
      }
    ]
  }
}
```

### 5.3 删除字典类型

#### 5.3.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | DELETE |
| 请求地址 | `/admin/system/dicts/types/{dictType}` |

#### 5.3.2 路径参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| dictType | string | M | `merchant_status` | 字典类型编码 |

#### 5.3.3 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": null
}
```

### 5.4 保存或更新字典数据

#### 5.4.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/dicts/data` |

#### 5.4.2 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| dictType | string | M | `merchant_status` | 字典类型编码 |
| dictLabel | string | M | `正常` | 字典展示标签 |
| dictValue | string | M | `1` | 字典业务值 |
| parentValue | string | O | `null` | 父级字典值 |
| locale | string | O | `zh-CN` | 语言区域，默认 `zh-CN` |
| dictSort | integer | O | `1` | 排序 |
| listClass | string | O | `success` | 前端展示样式 |
| extraJson | string | O | `{"color":"green"}` | 扩展 JSON |
| isDefault | integer | O | `1` | 是否默认 |
| status | integer | O | `1` | 状态 |
| remark | string | O | `商户可正常交易` | 备注 |
| operator | string | O | `admin` | 当前操作人 |

#### 5.4.3 请求示例

```json
{
  "dictType": "merchant_status",
  "dictLabel": "正常",
  "dictValue": "1",
  "locale": "zh-CN",
  "dictSort": 1,
  "listClass": "success",
  "extraJson": "{\"color\":\"green\"}",
  "isDefault": 1,
  "status": 1,
  "remark": "商户可正常交易",
  "operator": "admin"
}
```

#### 5.4.4 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "id": 1,
    "dictType": "merchant_status",
    "dictLabel": "正常",
    "dictValue": "1",
    "parentValue": null,
    "locale": "zh-CN",
    "dictSort": 1,
    "listClass": "success",
    "extraJson": "{\"color\":\"green\"}",
    "isDefault": 1,
    "status": 1,
    "remark": "商户可正常交易",
    "createdAt": "2026-06-06T10:00:00",
    "updatedAt": "2026-06-06T10:00:00"
  }
}
```

### 5.5 查询字典数据列表

#### 5.5.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/dicts/data/search` |

#### 5.5.2 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| dictType | string | O | `merchant_status` | 字典类型编码 |
| dictLabel | string | O | `正常` | 字典标签，右模糊查询 |
| dictValue | string | O | `1` | 字典业务值 |
| parentValue | string | O | `null` | 父级字典值 |
| locale | string | O | `zh-CN` | 语言区域 |
| status | integer | O | `1` | 状态 |
| pageNo | integer | O | `1` | 当前页码 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 5.5.3 请求示例

```json
{
  "dictType": "merchant_status",
  "locale": "zh-CN",
  "status": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

#### 5.5.4 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "total": 1,
    "pageNo": 1,
    "pageSize": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "dictType": "merchant_status",
        "dictLabel": "正常",
        "dictValue": "1",
        "parentValue": null,
        "locale": "zh-CN",
        "dictSort": 1,
        "listClass": "success",
        "extraJson": "{\"color\":\"green\"}",
        "isDefault": 1,
        "status": 1,
        "remark": "商户可正常交易",
        "createdAt": "2026-06-06T10:00:00",
        "updatedAt": "2026-06-06T10:00:00"
      }
    ]
  }
}
```

### 5.6 删除字典数据

#### 5.6.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | DELETE |
| 请求地址 | `/admin/system/dicts/data/{dictType}/{dictValue}?locale=zh-CN` |

#### 5.6.2 请求参数

| 字段 | 位置 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- | --- |
| dictType | Path | string | M | `merchant_status` | 字典类型编码 |
| dictValue | Path | string | M | `1` | 字典业务值 |
| locale | Query | string | O | `zh-CN` | 语言区域，不传默认 `zh-CN` |

#### 5.6.3 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": null
}
```

## 6. 操作日志接口

### 6.1 写入操作日志

#### 6.1.1 接口说明

写入后台操作日志。该接口主要供后台管理系统、内部服务或特殊前端行为显式写入日志。

普通后台管理接口和商户管理接口可接入 `@OperationLog` 注解和 AOP 自动采集，前端一般不需要直接调用该接口。日志写入接口本身不会再次自动采集，避免形成递归日志。

#### 6.1.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/oper-logs` |

#### 6.1.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| traceId | string | O | `TRACE202606060001` | 链路追踪ID |
| requestId | string | O | `REQ202606060001` | 请求ID |
| merchantId | string | O | `200045` | 涉及商户时记录 |
| moduleName | string | O | `商户管理` | 模块名称 |
| businessType | integer | O | `2` | 业务类型 |
| methodName | string | O | `MerchantController.update` | 后端方法名称 |
| requestMethod | string | O | `POST` | 请求方式 |
| operatorType | integer | O | `1` | 操作人类别 |
| operatorId | string | O | `10001` | 操作人ID |
| operatorName | string | O | `admin` | 操作人名称 |
| operUrl | string | O | `/admin/merchant/update` | 请求URL |
| operIp | string | O | `127.0.0.1` | 操作IP |
| operLocation | string | O | `Shanghai` | 操作地点 |
| requestParam | string | O | `{"merchantId":"200045"}` | 脱敏后的请求参数 |
| responseResult | string | O | `{"code":"T200"}` | 脱敏后的响应结果 |
| costTime | long | O | `35` | 执行时长，毫秒 |
| status | integer | O | `1` | 操作状态：0失败，1成功 |
| errorCode | string | O | `F402001` | 错误码 |
| errorMsg | string | O | `Invalid request parameter` | 错误信息 |

#### 6.1.4 请求示例

```json
{
  "traceId": "TRACE202606060001",
  "requestId": "REQ202606060001",
  "merchantId": "200045",
  "moduleName": "商户管理",
  "businessType": 2,
  "methodName": "MerchantController.update",
  "requestMethod": "POST",
  "operatorType": 1,
  "operatorId": "10001",
  "operatorName": "admin",
  "operUrl": "/admin/merchant/update",
  "operIp": "127.0.0.1",
  "operLocation": "Shanghai",
  "requestParam": "{\"merchantId\":\"200045\",\"merchantName\":\"测试商户\"}",
  "responseResult": "{\"code\":\"T200\",\"message\":\"Success\"}",
  "costTime": 35,
  "status": 1
}
```

#### 6.1.5 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": null
}
```

### 6.2 查询操作日志列表

#### 6.2.1 接口说明

按条件分页查询后台操作日志。服务端单页最大限制为 500 条，避免后台列表误查大结果集。

#### 6.2.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/oper-logs/search` |

#### 6.2.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| traceId | string | O | `TRACE202606060001` | 链路追踪ID |
| requestId | string | O | `REQ202606060001` | 请求ID |
| merchantId | string | O | `200045` | 商户号 |
| operatorId | string | O | `10001` | 操作人ID |
| moduleName | string | O | `商户管理` | 模块名称 |
| businessType | integer | O | `2` | 业务类型 |
| status | integer | O | `1` | 操作状态 |
| operatedStartAt | string | O | `2026-06-06T00:00:00` | 操作开始时间 |
| operatedEndAt | string | O | `2026-06-06T23:59:59` | 操作结束时间 |
| pageNo | integer | O | `1` | 当前页码 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 6.2.4 请求示例

```json
{
  "merchantId": "200045",
  "businessType": 2,
  "status": 1,
  "operatedStartAt": "2026-06-06T00:00:00",
  "operatedEndAt": "2026-06-06T23:59:59",
  "pageNo": 1,
  "pageSize": 20
}
```

#### 6.2.5 响应参数

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键ID |
| traceId | string | 链路追踪ID |
| requestId | string | 请求ID |
| merchantId | string | 商户号 |
| moduleName | string | 模块名称 |
| businessType | integer | 业务类型 |
| requestMethod | string | 请求方式 |
| operatorId | string | 操作人ID |
| operatorName | string | 操作人名称 |
| operUrl | string | 请求URL |
| operIp | string | 操作IP |
| costTime | long | 执行时长，毫秒 |
| status | integer | 操作状态 |
| errorCode | string | 错误码 |
| errorMsg | string | 错误信息 |
| operatedAt | string | 操作时间 |

#### 6.2.6 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "total": 1,
    "pageNo": 1,
    "pageSize": 20,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "traceId": "TRACE202606060001",
        "requestId": "REQ202606060001",
        "merchantId": "200045",
        "moduleName": "商户管理",
        "businessType": 2,
        "requestMethod": "POST",
        "operatorId": "10001",
        "operatorName": "admin",
        "operUrl": "/admin/merchant/update",
        "operIp": "127.0.0.1",
        "costTime": 35,
        "status": 1,
        "errorCode": null,
        "errorMsg": null,
        "operatedAt": "2026-06-06T10:00:00"
      }
    ]
  }
}
```

### 6.3 查询登录日志列表

#### 6.3.1 接口说明

按条件分页查询后台登录日志。服务端单页最大限制为 500 条。

#### 6.3.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/system/login-logs/search` |

#### 6.3.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| appId | long | O | `1` | 系统应用ID |
| loginAccount | string | O | `admin` | 登录账号，支持右模糊查询 |
| loginIp | string | O | `127.0.0.1` | 登录IP |
| merchantId | string | O | `200045` | 商户号 |
| loginStatus | integer | O | `1` | 登录状态：0失败，1成功 |
| loginStartAt | string | O | `2026-06-06T00:00:00` | 登录开始时间 |
| loginEndAt | string | O | `2026-06-06T23:59:59` | 登录结束时间 |
| pageNo | integer | O | `1` | 当前页码 |
| pageSize | integer | O | `20` | 每页记录数，最大 500 |

#### 6.3.4 响应参数

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键ID |
| appId | long | 系统应用ID |
| accountId | long | 账号ID |
| userId | long | 用户主体ID |
| merchantId | string | 商户号 |
| loginAccount | string | 登录账号 |
| loginIp | string | 登录IP |
| userAgent | string | User-Agent |
| loginStatus | integer | 登录状态 |
| failReason | string | 失败原因 |
| loginAt | string | 登录时间 |

### 6.4 操作日志自动采集

#### 6.4.1 采集说明

系统已在 `component-web` 提供 `@OperationLog` 注解和 AOP 自动采集能力。后台管理、商户管理等管理类接口只要在 Controller 方法上声明该注解，系统会自动记录模块、业务类型、请求路径、请求方式、操作人、请求参数、耗时、成功失败状态和异常信息。

当前 `service-admin` 已接入自动采集的接口包括系统配置、数据字典和操作日志查询接口。`service-merchant` 已提供操作日志上报记录器，后续商户管理端 Controller 使用同一个 `@OperationLog` 即可采集。支付交易 OpenAPI 接口不属于管理类系统，不接入操作日志采集。

#### 6.4.2 注解示例

```java
@OperationLog(
        moduleName = "系统配置",
        businessType = OperationTypeConstants.UPDATE,
        operation = "保存或更新系统参数配置"
)
@PostMapping("/configs")
public CommonResult<SysConfigDTO> saveConfig(@RequestBody SysConfigSaveRequest request) {
    return CommonResult.success(configService.saveConfig(request));
}
```

#### 6.4.3 自动采集请求头

| 请求头 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| X-Trace-Id | O | `TRACE202606060001` | 链路追踪ID |
| X-Request-Id | O | `REQ202606060001` | 请求ID |
| X-Operator-Id | O | `10001` | 当前操作人ID |
| X-Operator-Name | O | `admin` | 当前操作人名称 |
| X-Operator-Type | O | `1` | 操作人类别：1后台用户，2商户用户，3系统任务 |
| X-Merchant-Id | O | `200045` | 当前操作涉及商户时记录 |

#### 6.3.4 采集边界

1. 操作日志 AOP 只采集标注 `@OperationLog` 的管理类接口。
2. `service-admin` 使用本地记录器直接写入 `sys_oper_log`。
3. `service-merchant` 使用上报记录器把商户管理端操作日志提交到 `service-admin` 的 `/admin/system/oper-logs`。
4. 支付交易 OpenAPI、渠道回调、健康检查等接口不标注 `@OperationLog`，不采集操作日志。
5. 商户用户操作建议使用 `operatorType=2`，同时写入 `merchantId`、`operatorId`、`operatorName`，方便后台按商户维度审计。
6. 自动采集会对请求参数和响应结果做脱敏、截断，禁止记录完整 JWT、token、私钥、密码、卡号、CVV 等敏感明文。

## 7. 当前限制和后续规划

### 7.1 当前限制

1. 后台登录态、菜单权限、按钮权限尚未在当前接口层实现。
2. 商户管理端操作日志当前通过 HTTP 上报到 `service-admin`，后续可升级为 MQ 异步事件，降低跨服务调用耦合。

### 7.2 后续规划

| 功能 | 说明 |
| --- | --- |
| 分页查询 | 已支持配置、字典、日志列表分页响应，后续继续补充筛选条件和导出 |
| 跨服务操作日志 | 将操作日志注解或事件模型沉淀到公共组件，支持商户系统、运营后台和系统任务统一审计 |
| 权限控制 | 接入后台用户、角色、菜单、按钮权限 |
| 敏感字段脱敏增强 | 持续扩展脱敏字段，覆盖 token、密钥指纹、证件号、手机号等更多字段 |
| 接口导出 | 支持操作日志导出、字典导出 |

## 8. 拒付预警配置

接口前缀为 `/admin/risk/dispute-alert`。登录账号须具备相应功能权限及平台全量商户数据范围。配置读取使用主库，写入记录操作人。修改请求携带最近读取的 `version`，过期版本返回冲突。账号资料、账号产品和凭据共用账号版本，客户端收到成功响应后使用最新版本继续操作。

| 方法与路径 | 业务 | 权限 |
| --- | --- | --- |
| `GET /platforms`、`GET /platforms/{id}` | 查询平台类型、产品、卡品牌和 Logo 版本 | `channel:alert-platform:list` |
| `POST /platforms`、`POST /platforms/with-logo` | 新增平台及平台产品，可同时上传 Logo | `channel:alert-platform:add` |
| `PUT /platforms/{id}`、`PUT /platforms/{id}/with-logo` | 维护平台资料和产品，可替换或移除 Logo | `channel:alert-platform:edit` |
| `GET /platforms/card-brand-options` | 查询启用的卡品牌字典项，`locale` 支持中文与英文 | `channel:alert-platform:list` |
| `GET /platforms/{id}/logo` | 读取平台私有 PNG | `channel:alert-platform:list` |
| `GET /accounts`、`GET /accounts/{id}` | 查询账号、产品及凭据登记状态 | `channel:alert-platform:list` |
| `POST /accounts`、`PUT /accounts/{id}`、`PUT /accounts/{id}/products/{product}` | 新增、维护和按产品启停 | `channel:alert-platform:account` |
| `POST /accounts/provider`、`PUT /accounts/{id}/provider` | 保存或启用 Austreme、Nuvei、Chargeflow、Chargebackhit 的账号、产品和专用接入配置 | `channel:alert-platform:account`，提交新密钥还需 `channel:alert-platform:credential` |
| `PUT /accounts/{id}/credentials` | 登记或替换 Verxa Shield 账号的共用公私钥 | `channel:alert-platform:credential` |
| `GET /mids/channels`、`GET /mids`、`GET /source-options` | 查询收单渠道、按渠道和关键词查询银行卡收单 MID，以及指定产品和环境的可用来源账号 | `channel:dispute-alert:list` |
| `GET /mid-bindings`、`GET /mid-bindings/{id}` | 查询绑定、多条标识和共享标识提示 | `channel:dispute-alert:list` |
| `POST /mid-bindings`、`PUT /mid-bindings/{id}` | 登记或维护开通范围和对应标识 | 分别为 `channel:dispute-alert:add`、`channel:dispute-alert:edit` |
| `PUT /mid-bindings/{id}/status` | 确认上游开通和有效映射后启用，或停用绑定 | `channel:dispute-alert:status` |

### 8.1 平台、产品及 Logo

平台用稳定编码标识，类型支持 `AGGREGATOR`、`ACQUIRER_PSP`、`NETWORK_DIRECT`。每项平台产品独立维护覆盖确认状态、卡品牌和启用状态。Logo 表单采用 `multipart/form-data`，`metadata` 为 JSON，`file` 为可选 PNG 或 JPEG，更新时可传 `removeLogo=true`。平台资料和图片引用一起提交。列表和详情返回 `logoVersion`，Logo GET 返回受保护的 `image/png`。图片文件沿用渠道 Logo 的校验、扫描及私有对象存储。

### 8.2 接入账号和凭据

当前部署内，同一平台以商户号区分接入账号。账号列表和新增请求使用 Admin 启动环境自动确定归属，账号与绑定来源的响应使用平台名和商户号标识账号。一个账号可登记多个平台产品，各产品独立启停并保存交付参数。Verxa Shield 建议先以草稿登记商户号与产品，再通过凭据接口提交 `version`、`publicKey`、`privateKey`，然后按需启用账号及产品。首次需同时登记公钥和私钥，后续允许只替换其中一项。凭据写入返回登记状态及凭据版本，更新后的账号版本从账号查询获取。

Admin 使用已配置的持久化风控主密钥（`payment.risk.secret`）加密公私钥，将密文及版本信息写入凭据版本表。AES-GCM 密文绑定账号、材料引用和公钥或私钥类型。仅替换一项时，沿用另一项的密文和材料引用。凭据写入与更新账号当前版本指针在同一数据库事务中完成。凭据接口仅返回登记状态，操作日志不采集凭据接口的请求正文或响应正文。

已有一期表的部署先执行 [凭据密文列迁移](../../../service-admin/src/main/resources/sql/dispute-alert-credential-cipher-migration.sql)。历史版本仅有外部引用时，后台保留其账号和版本记录，并要求在原账号中重新登记公钥和私钥。

#### 四家平台专用接入配置

账号写入使用 `merchantNumber`、`status`、`products`、`settings` 和仅写的 `credentials`。新增提供 `platformId`，维护提供最近读取的账号 `version`。产品项包含 `product` 和各自的 `status`。草稿需要账号标识和至少一个关联产品。启用按平台、对接用途和 API 版本校验已登记凭据及必要设置。Austreme 的交付和认证待确认项可留空。已登记的产品通过停用维护，保留已有 MID 引用。

`settings` 接受各平台普通字段，`credentials` 接受当前配置对应的密钥字段。密钥原文复用 Admin 风控主密钥进行认证加密，密文按账号、平台与字段隔离保存。缺省和留空沿用原密文。仅在填写新凭据时创建新版本，同时更新账号的当前版本指针。账号、各产品及平台配置在同一事务提交。读取和保存响应的 `providerConfig` 包含普通参数 `values`、已登记的凭据字段名 `registered` 和启用校验结果 `activationValidated`。写入日志排除请求体和响应体。

部署新版 Admin 前执行 [平台接入配置建表脚本](../../../service-admin/src/main/resources/sql/dispute-alert-provider-account-migration.sql)。脚本复用现有接入账号和凭据版本表，已有账号、公私钥版本及 MID 绑定记录保留。历史上已启用的这四家账号须打开新表单校验启用，之后其已启用产品才可作为可用来源。回退应用版本时保留现有表内新增字段的配置和密文。上线前保持旧版应用和现有表结构即可继续原有流程。

### 8.3 渠道 MID 绑定和部署状态

Admin 根据当前 Spring Profile 自动选择同环境来源账号。`dev`、`test`、`uat` 和 `sample` 使用 `TEST` 来源，`prod` 使用 `PRODUCTION` 来源。直接沿用 Admin 的部署环境即可。无法识别部署环境或同时激活生产和非生产 Profile 时，可查看已有绑定，来源查询为空，新增和启用绑定会被拒绝。

绑定现有渠道 MID 主键与已启用的账号产品，外部商户范围用于标识同一来源内的隔离范围。当前支持 Ethoca 和 RDR。每条绑定保存多条真实收单 MID 或账单描述。RDR 还可登记收单机构 BIN 与 CAID，两个字段均为字符串。绑定新增为草稿，启用前须确认上游开通、留下依据并登记至少一条启用的标识。上游停用会影响绑定有效性，原绑定及开通资料仍可查询。详情返回同来源、同外部商户范围下共享有效标识的其他已启用 MID，供运营核对。

建表与凭据密文列分别见 [一期建表脚本](../../sql/20261125_dispute_alert_configuration_review.sql) 和 [凭据密文列迁移](../../../service-admin/src/main/resources/sql/dispute-alert-credential-cipher-migration.sql)。管理端通过上述接口配置平台、账号、凭据和 MID 绑定。平台 Logo 使用私有对象存储。

# Admin / Merchant 登录注册与权限接口文档

## 1. 文档说明

### 1.1 适用范围

本文档适用于支付框架内部管理类系统：

- `service-admin`：平台管理后台。
- `service-merchant`：商户管理系统。

支付 OpenAPI 接口不使用本文档的登录 token，仍使用商户 OpenAPI 的 JWT HS256 + 请求体加密标准。

### 1.2 认证模型

系统采用“应用隔离 + 用户主体 + 登录账号 + RBAC”的模型：

- `sys_app` 区分 `ADMIN` 和 `MERCHANT`。
- `sys_user` 表示自然人或用户主体。
- `sys_account` 表示某个用户在某个系统下的登录账号。
- `sys_role`、`sys_menu`、`sys_permission` 分别管理角色、菜单和后端权限。
- 商户系统账号通过 `sys_account.merchant_id` 绑定已有 `base_merchant_info.merchant_id`，不重复创建商户主表。
- `sys_merchant_user`、`sys_merchant_user_role` 保留商户端用户与 `base_merchant_info.id` 的兼容绑定关系，业务登录仍以 `sys_account` 为准。

### 1.3 自动鉴权

`service-admin` 和 `service-merchant` 已接入 Spring MVC Interceptor 自动鉴权：

- 白名单：登录接口、健康检查、Swagger/OpenAPI 文档、静态资源、`/error`。
- 非白名单接口必须携带 `Authorization: Bearer {accessToken}`。
- token 缺失、格式错误、无效、过期或已退出返回 `401`。
- 后端接口优先读取 Controller 方法上的 `@RequiresPermission` 权限标记；未标记时再回退到 `sys_permission.resource_method/resource_path` 路径匹配。
- 当前账号缺少接口要求的权限时返回 `403`。
- 鉴权成功后，服务端写入 `InternalAuthContextHolder`，包含当前账号、用户、应用、商户号、角色集合和权限集合。
- 支付 OpenAPI 的商户 JWT/HMAC/RSA 加密鉴权与本文档后台登录 token 独立，不混用。

### 1.4 公共响应格式

```json
{
  "code": "T200",
  "message": "Success",
  "data": {}
}
```

### 1.5 Token 使用方式

登录成功后返回 `accessToken`，前端后续请求放入请求头：

```http
Authorization: Bearer {accessToken}
```

服务端数据库仅保存 `token_hash`，不保存 token 明文。

### 1.6 字段必填说明

| 标识 | 说明 |
| --- | --- |
| M | 必填 |
| O | 可选 |
| C | 条件必填 |

## 2. 管理后台登录注册接口

### 2.1 注册管理后台账号

#### 2.1.1 接口说明

注册 `ADMIN` 应用下的后台账号。默认绑定角色 `ADMIN_OPERATOR`。

#### 2.1.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/auth/register` |
| 是否加密 | 否，内部接口不加密 |

#### 2.1.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| loginAccount | string | M | `admin001` | 登录账号 |
| password | string | M | `Admin@123456` | 登录密码，8-64位 |
| realName | string | M | `Scott Admin` | 用户姓名 |
| mobile | string | O | `13800000000` | 手机号 |
| email | string | O | `admin@example.com` | 邮箱 |
| roleCode | string | O | `ADMIN_OPERATOR` | 角色编码，不传使用默认角色 |
| operator | string | O | `system` | 操作人 |

#### 2.1.4 请求示例

```json
{
  "loginAccount": "admin001",
  "password": "Admin@123456",
  "realName": "Scott Admin",
  "mobile": "13800000000",
  "email": "admin@example.com"
}
```

#### 2.1.5 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "accountId": 10001,
    "userId": 20001,
    "appCode": "ADMIN",
    "loginAccount": "admin001",
    "realName": "Scott Admin",
    "merchantId": null,
    "status": 1
  }
}
```

### 2.2 管理后台登录

#### 2.2.1 接口说明

后台账号登录。登录成功后返回 token、账号信息、菜单树、权限编码集合。

#### 2.2.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/auth/login` |
| 是否加密 | 否，内部接口不加密 |

#### 2.2.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| loginAccount | string | M | `admin001` | 登录账号 |
| password | string | M | `Admin@123456` | 登录密码 |

#### 2.2.4 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "accessToken": "JMLlJzaad5yxKq1fQErxgiLdYt4JxA9LqS9V4rA9FrE",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "expireAt": "2026-06-06T12:00:00",
    "account": {
      "accountId": 10001,
      "userId": 20001,
      "appCode": "ADMIN",
      "loginAccount": "admin001",
      "realName": "Scott Admin",
      "merchantId": null,
      "status": 1
    },
    "menus": [
      {
        "id": 1,
        "parentId": 0,
        "menuCode": "admin_dashboard",
        "menuName": "控制台",
        "menuType": "MENU",
        "routePath": "/dashboard",
        "componentPath": "admin/dashboard/index",
        "permissionCode": "admin:dashboard:view",
        "icon": "dashboard",
        "sortNo": 1,
        "children": []
      }
    ],
    "permissions": [
      "admin:dashboard:view",
      "admin:user:create",
      "admin:user:view"
    ]
  }
}
```

### 2.3 查询当前后台用户

#### 2.3.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | GET |
| 请求地址 | `/admin/auth/me` |
| 请求头 | `Authorization: Bearer {accessToken}` |

#### 2.3.2 响应说明

响应结构同 `2.2 管理后台登录`，但 `accessToken` 字段为空。

### 2.4 管理后台退出登录

#### 2.4.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/admin/auth/logout` |
| 请求头 | `Authorization: Bearer {accessToken}` |

#### 2.4.2 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": null
}
```

## 3. 商户系统登录注册接口

### 3.1 注册商户系统账号

#### 3.1.1 接口说明

注册 `MERCHANT` 应用下的商户系统账号。该接口不会创建商户主表，`merchantId` 必须已经存在于 `base_merchant_info`，且商户状态可用。

默认优先绑定当前商户专属角色 `MERCHANT_ADMIN_{merchantId}`；如果数据库尚未初始化商户专属角色，则回退到通用 `MERCHANT_ADMIN`。

#### 3.1.2 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/merchant/auth/register` |
| 是否加密 | 否，内部接口不加密 |

#### 3.1.3 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| merchantId | string | M | `200045` | 已开户商户号，必须存在于 `base_merchant_info` |
| loginAccount | string | M | `merchant-admin` | 登录账号 |
| password | string | M | `Merchant@123456` | 登录密码，8-64位 |
| realName | string | M | `Merchant Admin` | 用户姓名 |
| mobile | string | O | `13900000000` | 手机号 |
| email | string | O | `merchant@example.com` | 邮箱 |
| roleCode | string | O | `MERCHANT_ADMIN` | 角色编码，不传使用默认角色 |
| operator | string | O | `system` | 操作人 |

#### 3.1.4 请求示例

```json
{
  "merchantId": "200045",
  "loginAccount": "merchant-admin",
  "password": "Merchant@123456",
  "realName": "Merchant Admin",
  "mobile": "13900000000",
  "email": "merchant@example.com"
}
```

#### 3.1.5 响应示例

```json
{
  "code": "T200",
  "message": "Success",
  "data": {
    "accountId": 30001,
    "userId": 40001,
    "appCode": "MERCHANT",
    "loginAccount": "merchant-admin",
    "realName": "Merchant Admin",
    "merchantId": "200045",
    "status": 1
  }
}
```

### 3.2 商户系统登录

#### 3.2.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/merchant/auth/login` |
| 是否加密 | 否，内部接口不加密 |

#### 3.2.2 请求参数

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| loginAccount | string | M | `merchant-admin` | 登录账号 |
| password | string | M | `Merchant@123456` | 登录密码 |
| merchantId | string | O | `200045` | 传入时要求账号必须属于该商户 |

#### 3.2.3 响应说明

响应结构同 `2.2 管理后台登录`，但 `appCode` 为 `MERCHANT`，菜单与权限为商户系统菜单权限。

### 3.3 查询当前商户用户

#### 3.3.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | GET |
| 请求地址 | `/merchant/auth/me` |
| 请求头 | `Authorization: Bearer {accessToken}` |

### 3.4 商户系统退出登录

#### 3.4.1 请求说明

| 项目 | 内容 |
| --- | --- |
| 请求方式 | POST |
| 请求地址 | `/merchant/auth/logout` |
| 请求头 | `Authorization: Bearer {accessToken}` |

## 4. 权限返回说明

### 4.1 菜单树

`menus` 用于前端动态路由和菜单展示。

| 字段 | 说明 |
| --- | --- |
| id | 菜单ID |
| parentId | 父级菜单ID，0表示顶级 |
| menuCode | 菜单编码 |
| menuName | 菜单名称 |
| menuType | 菜单类型：CATALOG、MENU、BUTTON、LINK |
| routePath | 前端路由 |
| componentPath | 前端组件路径 |
| permissionCode | 前端按钮权限标识 |
| icon | 图标 |
| children | 子菜单 |

### 4.2 权限编码

`permissions` 用于前端按钮控制和后端接口鉴权。

示例：

```json
[
  "merchant:dashboard:view",
  "merchant:transaction:view",
  "merchant:settlement:view"
]
```

## 5. 错误码

| 错误码 | 场景 |
| --- | --- |
| F401 | 账号不存在、密码错误、账号停用、账号锁定、token无效或过期 |
| F401001 | 缺少 Authorization |
| F402001 | 参数非法，例如账号已存在 |
| F402002 | 必填参数缺失 |
| F404 | 应用、角色等配置不存在 |
| F500 | 系统内部异常 |

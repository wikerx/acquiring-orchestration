# 上传安全设施部署

管理端、商户端的用户上传统一执行会话和功能/对象权限 → 场景格式、大小校验 → ClamAV 扫描。头像、Logo 在原文件通过后进行有界解码和 PNG 重编码，生成图再次扫描后才写入独立私有 Bucket。历史文件没有扫描记录，不视为已通过；补扫应另行安排。

## 环境与配置

| 环境 | 扫描地址 | 存储和启动行为 |
| --- | --- | --- |
| 本机开发 | `127.0.0.1:3310` | 使用本文 Compose；扫描器缺失不阻止服务启动，但所有用户上传会拒绝。 |
| 自动测试 | 注入测试扫描器，协议测试使用回环 TCP 服务 | 不连接生产存储；真实病毒引擎测试显式开启。 |
| 测试、生产 | 受控内网的 clamd 地址 | 不允许公网访问；扫描失败、病毒库过期、禁用扫描都拒绝上传。 |

两端均加载以下 Nacos 公共配置，默认值也是拒绝绕过的安全值：

```yaml
acquiring:
  upload-security:
    enabled: true
    host: ${UPLOAD_SCAN_HOST:127.0.0.1}
    port: ${UPLOAD_SCAN_PORT:3310}
    timeout-millis: 10000
    max-concurrent-scans: 4
    uploads-per-minute: 20
    max-file-bytes: 20971520
    max-database-age-hours: 72
    scanner-timezone: UTC
  object-storage:
    media-bucket: ${OBJECT_STORAGE_MEDIA_BUCKET:vexra-media-files}
```

`enabled: false` 只表示上传不可用，不是免扫描开关。病毒库构建时间按扫描容器 UTC 解析；数据库超过 72 小时或时间异常即拒绝。`uploads-per-minute` 是每个应用账号、每个服务实例的限制，集群入口仍应叠加网关限流。每个实例最多 4 个并发扫描、2 个并发图片解码。Servlet 请求仍受公共配置 20 MiB 单文件、21 MiB 请求上限限制。代理也应设置请求体上限，鉴权不依赖文件内容解析。

## 启动和验证

在本目录运行 `docker compose up -d`。默认官方 Alpine 镜像在 Apple Silicon 上需要 `linux/amd64` 仿真，本地 Compose 已显式指定；使用原生 ARM 镜像时应同时覆盖 `CLAMAV_IMAGE`、`CLAMAV_PLATFORM` 并重新验证。官方镜像包含 FreshClam；首次准备病毒库及加载引擎可能需要数分钟，必须等健康检查通过。默认镜像固定到本次验证的官方 ClamAV 1.5.4 镜像摘要；升级时通过 `CLAMAV_IMAGE` 指定经验证的新摘要。健康检查显式使用容器 IPv4 回环地址，避免镜像默认 `localhost` 优先解析到 IPv6 导致误报。

`clamd.conf` 的 `AlertExceedsMax yes` 是必需项，防止被扫描器跳过的文件被误认为安全。`StreamMaxLength`、`MaxFileSize`、`MaxScanSize` 必须覆盖业务允许的文件及其受限展开内容，扫描超限不能放行。加密文档无法完成内容检查时拒绝上传。不要开放 clamd 的公网端口：它的 TCP 协议没有认证和加密。

可使用真实本机 ClamAV 执行针对性验证：

```bash
mvn -pl component-library/component-web -am -Dtest=ClamAvContainerIntegrationTest -Dclamav.integration=true -Dsurefire.failIfNoSpecifiedTests=false test
```

此测试使用无害 EICAR 标准测试字节验证拒绝行为，不把测试字节写入文件服务器。普通单元测试里的 TCP 替身只证明协议和失败分支，不证明病毒库或引擎已实际部署。

## 私有图片存储

在现有 MinIO/S3 中创建 `vexra-media-files`，保持匿名访问关闭。它必须与 KYB Bucket 和模板 Bucket 不同。两端服务账号需要此 Bucket 内的 `GetObject`、`PutObject`、`DeleteObject` 权限。凭证仍由现有安全配置注入，禁止放进仓库或日志。可使用已配置好凭证的 `mc` 别名执行 `mc mb --ignore-existing <别名>/vexra-media-files` 和 `mc anonymous set none <别名>/vexra-media-files`。

图片对象使用后端生成的随机标识和 SHA-256，数据库保存内部引用；前端只收到不含路径的版本号。读取通过各自鉴权接口返回 `image/png` 和 `no-store`，不使用公开 URL、外部图片抓取或携带会话令牌的链接。图片内容与引用摘要不一致时拒绝返回。

## 发布顺序与兼容

1. 执行并核对 `docs/sql/20261116_channel_logo_migration.sql`，只新增可空字段；旧渠道默认无 Logo。
2. 创建独立私有图片 Bucket，配置最小权限，部署并验证 ClamAV 和病毒库更新。
3. 更新两端服务及前端。原 JSON 渠道保存接口保留，新 multipart 接口在同一事务保存渠道与 Logo；旧调用不修改 Logo。
4. 验证上传、移除、扫描超时、伪装格式、权限拒绝和图片读取。扫描不可用时已有图片读取及其他业务仍可工作。

用户主体的 `sys_user.avatar_url` 保存受管内部引用，旧外部地址不被下载或抓取。确实共用同一个 userId 的账号共用头像；用户名相同不意味着同一个用户。普通资料更新禁止覆盖头像字段，头像更新使用旧引用条件更新防止并发覆盖。

新图片写入失败或数据库事务回滚时清理新对象；数据库提交后才清理旧对象。清理失败记录 `IMAGE_OBJECT_CLEANUP_FAILED` 和非敏感图片版本号，应配置告警并由运维核对数据库引用后补偿清理，不可直接按对象年龄批量删除仍被引用的图片。

回退时先关闭上传入口、回退应用，保留新增可空列和私有图片。禁止为了恢复上传而关闭扫描并放行。停止本地扫描器可运行 `docker compose stop`，保留病毒库卷。

## 接口与交互

- `GET/POST/DELETE /admin/auth/avatar`、`/merchant/auth/avatar`：只操作当前用户，POST 使用 `file`。
- 登录和个人资料响应的 `account.avatarVersion` 为可选字段，不含存储地址。
- `POST /admin/channel/info/with-logo`、`PUT /admin/channel/info/{id}/with-logo`：JSON `metadata` part、可选 `file`，更新可指定 `removeLogo=true`；禁止同时上传和移除。
- `GET /admin/channel/info/{id}/logo`：沿用渠道列表权限；响应 `logoVersion` 为可选版本号。
- 图片只允许 PNG/JPEG，原文件最多 2 MiB、边长不超过 4096、总像素不超过 1600 万；头像居中裁剪为 512 方图，Logo 等比例缩放到最长边 1024，成品再次限制 2 MiB。
- 选图仅在浏览器本地预览；取消不产生服务器对象。头像在“保存头像”时提交；渠道图片随渠道表单一起保存。
- KYB 上传允许 PDF/PNG/JPEG，保持配置的 20 MiB 上限；模板按既有模板类型校验。风控导入只允许 CSV/XLS/XLSX、最多 5 MiB，汇率规则导入只允许 XLS/XLSX、最多 2 MiB。文件扫描通过后才进入现有业务解析。

## 运行监控

监控扫描服务健康、病毒库更新时间、扫描耗时、超时率、拒绝率、容器内存和对象清理失败。官方建议至少 3 GiB、优先 4 GiB 内存，因此本地 Compose 限额 4 GiB；病毒库重载时内存会增加。FreshClam 更新失败需要告警，不能依赖“进程仍在”判断健康。

依据：[ClamAV 官方部署说明](https://docs.clamav.net/manual/Installing/Docker.html)、[ClamD 协议](https://docs.clamav.net/manual/Usage/ClamdProtocol.html)、[OWASP 文件上传安全指南](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)。

# 分支与发布管理规范

## 分支模型

| 分支 | 用途 | 保护要求 |
| --- | --- | --- |
| `master` | 主分支、日常集成基线 | 禁止直接提交，所有变更通过 PR 合并 |
| `prod` | 生产发布基线 | 禁止直接提交，只允许由 release/hotfix 合并 |
| `feature/*` | 需求开发 | 从 `master` 拉出，完成后合并回 `master` |
| `bugfix/*` | 非生产缺陷修复 | 从 `master` 拉出，完成后合并回 `master` |
| `release/*` | 发布准备 | 从 `master` 拉出，验证通过后合并到 `prod` 和 `master` |
| `hotfix/*` | 生产紧急修复 | 从 `prod` 拉出，修复后合并到 `prod` 和 `master` |

## 命名规范

- 需求分支：`feature/{issue-id}-{short-desc}`
- 缺陷分支：`bugfix/{issue-id}-{short-desc}`
- 发布分支：`release/{yyyyMMdd|version}`
- 紧急修复：`hotfix/{issue-id}-{short-desc}`

示例：`feature/ACQ-102-channel-routing`

## 提交规范

提交信息使用：

```text
<type>(<scope>): <subject>
```

常用 `type`：

- `feat`：新功能
- `fix`：缺陷修复
- `docs`：文档
- `style`：格式调整
- `refactor`：重构
- `test`：测试
- `chore`：工程配置

示例：

```text
feat(route): add channel routing rule priority
```

## 合并要求

1. PR 必须关联需求、缺陷或任务编号。
2. PR 必须通过编译、单元测试、静态扫描和必要的集成测试。
3. 至少 1 名 Reviewer 批准后才可合并；支付、资金、安全相关变更至少 2 名 Reviewer。
4. 禁止将密钥、证书、真实卡号、真实账户、生产日志提交到仓库。
5. 合并到 `prod` 必须产生可追溯的版本标签和发布记录。

## 发布标签

每次生产发布必须在 `prod` 分支对应提交上创建发布标签：

```text
prod-yyyy-mm-dd
```

示例：

```text
prod-2026-05-16
```

同一天多次发布时，在日期后追加序号：

```text
prod-2026-05-16-2
```

推荐命令：

```bash
git checkout prod
git tag -a prod-2026-05-16 -m "release: prod 2026-05-16"
git push origin prod-2026-05-16
```

## 远程同步建议

首次初始化建议在本地执行：

```bash
git init -b master
git remote add origin https://github.com/wikerx/acquiring-orchestration.git
git add .
git commit -m "docs: initialize repository governance"
git push -u origin master
git checkout -b prod
git push -u origin prod
```

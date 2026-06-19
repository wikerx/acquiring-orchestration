---
name: docs-governance
description: 用于治理项目文档目录，统一中文文档规范、归档扫描报告、禁止在 docs 根目录散落临时文档，避免 AI 生成大量无效 Markdown。
---

# Docs Governance Skill

## 使用场景

当任务涉及 docs 目录、README、编码规范、API 文档、架构文档、扫描报告、修复报告、文档瘦身时使用本 Skill。

## 文档目录规则

长期有效文档放入：

```text
docs/architecture
docs/api/openapi
docs/api/internal
docs/standards
docs/deployment
docs/sql
```

一次性报告放入：

```text
docs/archive/reports
```

## 文档语言规则

1. 文档标题、章节标题、正文说明统一使用中文。
2. Java 类名、包名、方法名、接口路径、Header、JSON 字段、枚举值保留英文。
3. 技术名词可以保留英文，例如 JWT、OpenAPI、AES-256-GCM、RSA-OAEP-256、Redis、RocketMQ、Nacos。
4. 不要中英文标题混用。
5. 不要为了翻译而修改接口字段、代码标识符或配置项。

## 禁止行为

1. 不要在 docs 根目录新增临时报告。
2. 不要为小改动新增 Markdown 总结。
3. 不要生成 AI 风格模板化说明。
4. 不要把 PRD、会议纪要、临时需求讨论长期放在代码仓库。
5. 不要修改 Java / SQL / YAML 来配合文档整理。

## 输出要求

输出：

1. 已移动文件；
2. 已归档文件；
3. 已更新索引；
4. 是否修改业务代码；
5. 风险点。

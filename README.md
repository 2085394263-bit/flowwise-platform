# FlowWise 项目协作平台

[![Build](https://github.com/2085394263-bit/flowwise-platform/actions/workflows/build.yml/badge.svg)](https://github.com/2085394263-bit/flowwise-platform/actions/workflows/build.yml)

FlowWise 是一套独立实现的项目与任务协作平台，面向小团队提供项目看板、任务分派、审批流转、操作审计和角色权限。它有自己的产品名称、代码结构与数据模型；此仓库不包含从其他项目复制的业务源码。

## 当前版本

- Java 25 + Spring Boot 3.5，使用 Jakarta EE 命名空间；采用模块化单体结构，便于本地启动和后续按业务域拆分。
- Spring AI 1.1 + OpenAI 兼容模型：提供只读项目助手，通过工具调用按当前登录角色查询真实项目/任务数据；模型密钥从环境变量读取，默认关闭，不影响无 AI 配置时运行。
- AI 助手以悬浮面板贯穿工作台、项目、任务和审批页面；页面切换时当前浏览会话的对话内容保留。
- Spring Boot Actuator 提供受限的健康检查与基础运行状态；H2 控制台默认关闭，可按需显式启用。
- H2 文件数据库，首次启动自动写入演示项目与任务，重启后保留数据。
- 项目、任务、审批和活动记录 API。
- Spring Security 会话认证；账号保存在 H2 数据库，密码使用 BCrypt 哈希。
- 成员可公开注册并直接登录；项目经理/负责人可提交注册申请，管理员批准后启用对应权限。
- 管理员不能公开自助注册；首次启动初始化管理员，已登录管理员可创建其他管理员、项目经理和成员账号。
- CSRF Cookie 校验；角色权限同时在页面和服务端接口执行，并校验项目负责人及任务负责人。
- 页面按工作台、项目列表、单项目详情、任务列表和审批中心分开；项目详情只展示该项目的任务。
- 所有项目创建、修改和删除先提交申请，由管理员审批后生效；删除项目采用归档，保留历史任务数据。
- 管理员可管理全部项目和任务；项目经理只管理自己负责的项目；成员只可查看分配给自己的任务。
- 成员可开始任务并提交交付说明或链接；管理员或所属项目经理验收，通过后任务完成，驳回后退回处理中。
- 项目经理只处理自己项目的交付验收；待验收任务暂不允许编辑或删除。
- 页面每 5 秒自动同步一次，其他用户完成的项目审批和任务变更会更新到当前页面。
- 管理员、项目经理和成员使用不同工作台；员工工作台突出本人任务、进度和交付入口，收到新分派任务时即时提示。
- 表单提交防重复点击；项目名称不区分大小写且不能重复。

## 演示账号

> **仅限本地演示：** 首次启动会创建下列固定演示账号。不要将此配置用于公网部署或真实数据；演示账号及密码均为公开信息。

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 项目经理 | `manager` | `manager123` |
| 成员 | `member` | `member123` |

仅用于本地演示。部署到真实环境前，应替换账号、密码策略和数据库配置。当前成员可见范围依据已分配任务确定；正式团队部署可进一步增加显式项目成员关系和组织架构管理。

### 角色权限

| 操作 | 管理员 | 项目经理 / 负责人 | 成员 |
| --- | --- | --- | --- |
| 查看项目 | 全部项目 | 自己负责的项目 | 有本人任务的项目 |
| 创建项目 | 提交申请，等待管理员审批 | 提交申请，等待管理员审批 | 无权限 |
| 修改或删除项目 | 全部项目，审批后生效 | 仅自己负责的项目，审批后生效 | 无权限 |
| 创建、分派、编辑、删除任务 | 全部项目 | 自己负责的项目 | 无权限 |
| 开始任务、提交交付 | 仅本人被指派的任务 | 仅本人被指派的任务 | 仅本人被指派的任务 |
| 验收任务交付 | 全部项目 | 自己负责的项目 | 无权限 |
| 创建账号、审批项目与经理申请 | 有权限 | 无权限 | 无权限 |

交付提交时，说明或链接至少填写一项。验收通过后标记为已完成；驳回后任务退回进行中，负责人修改交付内容后可再次提交。

### 注册与账号管理

- 登录页提供成员注册和项目经理申请入口。账号名需为 3–32 位字母、数字或 `._-`，密码需为 8–72 位。
- 成员注册后可立即登录；项目经理申请在审批前不能登录，审批通过后获得项目经理权限。
- 管理员在“审批中心”审核项目经理申请，也可创建 ADMIN、MANAGER、MEMBER 账号。管理员账号不开放公开注册。

## 启动

建议使用 JDK 25（LTS）及 Maven 3.6.3+。Spring Boot 3.5 官方兼容范围为 Java 17–25。如果电脑同时装有旧版 Java，请先在当前 PowerShell 窗口选择新版 JDK（不要修改系统全局 PATH）：

```powershell
$env:JAVA_HOME = 'D:\develop\JDK' # 本机当前安装目录；其他电脑请换成 JDK 25 路径
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

然后在本目录打包并启动：

```powershell
mvn -DskipTests package
java -jar target\flowwise-platform-modern.jar
```

打开 <http://localhost:8099>。升级后的数据保存在 `data/flowwise-modern.mv.db`。旧版数据库 `data/flowwise.mv.db` 与升级前备份 `data/flowwise-before-java25-upgrade.mv.db` 均保留。H2 控制台默认关闭；确有本地排查需要时，在启动前设置 `$env:FLOWWISE_H2_CONSOLE_ENABLED='true'`，并且不要将其暴露到公网。

### 启用 AI 项目助手

项目助手默认关闭，不会在没有配置模型密钥时尝试外部调用。使用 PowerShell 环境变量临时启用（只对当前窗口有效，不要把密钥提交到仓库）：

```powershell
$env:SPRING_AI_MODEL_CHAT = 'openai'
$env:SPRING_AI_OPENAI_API_KEY = '你的模型服务密钥'
$env:SPRING_AI_OPENAI_CHAT_MODEL = 'gpt-4o-mini'
java -jar target\flowwise-platform-modern.jar
```

也可以配置兼容 OpenAI API 的服务地址：`$env:SPRING_AI_OPENAI_BASE_URL='服务商提供的地址'`。助手工具只开放读取，账号和角色信息由后端会话注入工具上下文，不交给模型作为可控输入；可见性过滤仍由服务层执行。`POST /api/agent/chat` 每次回答会产生模型服务费用，内容会发送给所配置的模型服务商。

## 主要 API

- `GET /api/dashboard`：工作台统计
- `POST /api/auth/register`：成员注册或提交项目经理注册申请
- `POST /api/admin/accounts`：管理员创建账号
- `GET /api/team/members`：管理员或项目经理获取可分派的有效成员员工账号
- `GET /api/admin/manager-registrations/pending`、`POST /api/admin/manager-registrations/{id}/decision`：管理员审核项目经理申请
- `GET|POST /api/projects`：查询项目、提交创建申请
- `PUT|DELETE /api/projects/{id}`：提交项目修改或删除申请
- `GET /api/project-requests/pending`、`POST /api/project-requests/{id}/decision`：管理员处理项目申请
- `GET|POST /api/projects/{id}/tasks`：查询和创建任务
- `PUT|DELETE /api/tasks/{id}`：编辑或删除任务（管理员/经理）
- `POST /api/tasks/{id}/start`：任务负责人开始任务
- `POST /api/tasks/{id}/delivery`：任务负责人提交交付说明或链接
- `GET /api/approvals/pending`：查询待处理审批
- `POST /api/approvals/{id}/decision`：通过或驳回审批（管理员/经理）
- `GET /api/activity`：操作记录
- `POST /api/agent/chat`：带角色范围的只读项目 Agent 问答（需配置模型服务）
- `GET /actuator/health`：运行健康状态

## 后续迭代方向

1. 将当前 H2 文件库迁移到 PostgreSQL，并为数据库变更引入 Flyway 版本迁移。
2. 为工作流加入可配置节点、超时提醒和幂等审批。
3. 增加显式项目成员关系、审计事件关联 ID 和结构化日志。
4. 增加 Docker Compose、CI 构建、API 文档以及 Agent 评测集和调用成本监控。

架构和角色边界详见 [docs/architecture.md](docs/architecture.md)。

开发与提交规范见 [CONTRIBUTING.md](CONTRIBUTING.md)，安全问题请阅读 [SECURITY.md](SECURITY.md)。项目采用 MIT License，详情见 [LICENSE](LICENSE)。

## 简历描述建议

以事实为准：模型密钥尚未配置时，只能描述 Agent 能力已经接入代码，不能宣称已完成真实模型联调。可以描述为“基于 Java 25、Spring Boot 3.5 和 Spring AI 构建带只读工具调用的项目协作助手；通过后端注入的登录身份与服务层权限过滤控制 Agent 数据可见范围；平台覆盖角色化工作台、任务交付和审批流转”。

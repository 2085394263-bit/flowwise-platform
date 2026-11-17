# 参与贡献

感谢你关注 FlowWise。欢迎通过 Issue 讨论缺陷和改进方向，再提交 Pull Request。

## 本地准备

- JDK 25
- Maven 3.6.3 或更高版本
- Git

克隆仓库后，在项目根目录运行：

```powershell
mvn -B verify
```

启动应用：

```powershell
mvn -DskipTests package
java -jar target/flowwise-platform-modern.jar
```

## 提交前

- 说明改动解决的问题以及用户可见的变化。
- 对权限、登录、项目/任务审批和 AI 工具调用的改动，描述相关角色和边界。
- 不提交数据库文件、日志、构建产物、个人配置或任何凭据。
- 保持与现有 Java 格式和中文界面文案风格一致。
- 若改动运行行为或配置方式，同步更新 README 或 `docs/` 文档。

Pull Request 会由 GitHub Actions 执行 Maven `verify` 构建。当前仓库没有承诺稳定 API 或生产部署兼容性，破坏性调整请在 PR 中明确说明。

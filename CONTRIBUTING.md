# 贡献指南

非常感谢您有兴趣为 Light Deploy 做出贡献！

## 如何贡献代码

1. **Fork** 本仓库到您的 GitHub/GitLab 账号下。
2. **克隆 (Clone)** 您 Fork 的仓库到本地：
   ```bash
   git clone https://github.com/your-username/light-deploy.git
   ```
3. **创建分支**：在进行任何修改之前，请从 `main` 或 `master` 分支切出一个新的分支：
   ```bash
   git checkout -b feature/your-feature-name
   # 或者
   git checkout -b fix/your-bug-fix
   ```
4. **提交代码**：请保持提交信息清晰、简洁。
   ```bash
   git commit -m "feat: add support for new git platform"
   ```
5. **推送到远程仓库**：
   ```bash
   git push origin your-branch-name
   ```
6. **发起 Pull Request (PR)**：在项目主页发起 PR，并描述您所做的修改、修复的 Bug 或新增的功能。

## 提交 Issue

如果您在使用过程中遇到了 Bug，或者有新功能的建议，请通过 **Issue** 的形式反馈给我们。
在提交 Issue 时，请尽可能详细地提供：
* 问题的具体表现
* 重现步骤
* 您所处的环境信息（操作系统、Node版本、Java版本等）

## 代码规范
* 前端代码：请遵循 Vue 3 + TypeScript 官方推荐规范，并确保 `npm run build` 无错误。
* 后端代码：请遵循标准的 Java / Spring Boot 编码规范。

再次感谢您的支持！
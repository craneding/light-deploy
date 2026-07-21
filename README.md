# Light Deploy - 基于 GitLab 的轻量级自动化部署系统

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Light Deploy 是一个能够与 GitLab 深度集成的自动化部署工具，支持多项目、多服务器的自动化编译、打包及 rsync 同步，并提供可视化的控制台和进度展示。

## GitLab OAuth2 应用程序配置指南

为了让系统能够使用 GitLab 授权登录，并允许系统调用 API 拉取您有权限的项目列表及分支信息，您需要在 GitLab 中创建一个 OAuth 应用程序，并将相关的配置填入本系统的后端配置中。

### 1. 在 GitLab 中新增 Application

1. 登录您的 GitLab 账号。
2. 点击右上角您的用户头像，选择 **Edit profile** (编辑个人资料)。
3. 在左侧导航栏中选择 **Applications** (应用程序)。
   > **注意**：如果您是私有化部署的 GitLab 管理员，且希望创建一个全局的应用，您也可以进入 **Admin Area** -> **Applications** 中进行创建。
4. 在 "Add new application" (添加新应用) 的表单中填写以下信息：
   - **Name**: `Light Deploy` (或者您喜欢的任何易于识别的名称)
   - **Redirect URI**: 填写后端的 OAuth 回调地址。如果您在本地开发环境运行，请填写：
     ```text
     http://localhost:8080/login/oauth2/code/gitlab
     ```
     *注意：如果您的后端部署在线上，请将 `http://localhost:8080` 替换为您线上的实际域名，例如 `https://api.yourdomain.com/login/oauth2/code/gitlab`。*
   - **Confidential**: 保持勾选状态（默认通常已勾选）。
   - **Scopes**: 必须勾选以下权限：
     - `read_user`: 用于获取登录用户的基本信息（如用户名、头像）。
     - `api` 或 `read_api`: 用于调用 GitLab API 以获取用户有权限的项目列表、分支、Commit 和 Tag 记录。
5. 填写完毕后，点击下方的 **Save application** (保存应用) 按钮。

### 2. 获取并配置凭证

1. 应用创建成功后，页面会跳转并显示 **Application ID** 和 **Secret**。这两个值非常重要，请不要泄露。
2. 在本项目中，打开后端项目的配置文件：`backend/src/main/resources/application.yml`。
3. 找到 `spring.security.oauth2.client.registration.gitlab` 节点，将您刚刚获取的 ID 和 Secret 填入：
   ```yaml
   spring:
     security:
       oauth2:
         client:
           registration:
             gitlab:
               client-id: "您在GitLab获取的 Application ID"
               client-secret: "您在GitLab获取的 Secret"
               # ... 其他配置保持不变
   ```

### 3. (可选) 私有化 GitLab 部署配置

如果您使用的是自己公司私有化部署的 GitLab (而不是官方的 gitlab.com)，您还需要在 `application.yml` 中修改 provider 的相关 URL，使其指向您的私有化 GitLab 地址：

打开 `backend/src/main/resources/application.yml`，找到 `provider.gitlab` 节点：
```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          gitlab:
            # 将以下 URL 中的 gitlab.com 替换为您自己的私有化 GitLab 域名
            authorization-uri: https://your-gitlab-domain.com/oauth/authorize
            token-uri: https://your-gitlab-domain.com/oauth/token
            user-info-uri: https://your-gitlab-domain.com/api/v4/user
```

## 项目构建与部署

### 后端构建
```bash
cd backend
mvn clean package -DskipTests
```
构建成功后，在 `backend/target` 目录下会生成 `backend-0.0.1-SNAPSHOT.jar`。

### 前端构建
```bash
cd frontend
npm install
npm run build
```
构建成功后，在 `frontend/dist` 目录下会生成静态文件。您可以将这些文件部署到 Nginx 等 Web 服务器上。

#### 环境变量加载机制
Vite 会根据当前运行的命令自动加载对应的环境变量文件：
- **`npm run dev`**: 默认处于 `development` 模式，会加载 `.env` 和 `.env.development`（如果存在）。
- **`npm run build`**: 默认处于 `production` 模式，会加载 `.env` 和 `.env.production`。
- **加载优先级**: `.env.[mode]` 的优先级高于 `.env`。即如果在 `.env.production` 中定义了与 `.env` 相同的变量，打包时会以 `.env.production` 中的值为准。

---

## 启动项目说明

### 后端启动
确保您已创建了 `light_deploy` 数据库，并导入了 `db/schema.sql` 结构。
```bash
cd backend
mvn clean spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run dev
```

> **提示**：前端默认配置了连接本地后端（`http://localhost:8080`）。环境变量配置位于 `frontend/.env`。如果在生产环境部署，可通过修改 `frontend/.env.production` 或在构建时指定 `VITE_API_BASE_URL` 环境变量来配置线上后端地址。

启动后，访问前端页面即可点击 "Login with GitLab" 体验完整的 OAuth 登录与自动化部署流程。

---

## 关于作者

本项目由 [craneding](https://github.com/craneding/) 开发与维护。

- **GitHub**: [https://github.com/craneding/](https://github.com/craneding/)
- **Blog**: [http://blog.dinghz.com](http://blog.dinghz.com)

欢迎关注我的 GitHub 账号，获取更多开源项目信息！

## 参与贡献

欢迎大家提交 Issue 或 Pull Request 来完善这个项目！在提交代码前，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解具体的贡献指南。

## 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。您可以自由地使用、修改和分发本项目。
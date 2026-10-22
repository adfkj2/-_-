# 统一认证登录（sso-platform）

多业务系统统一身份认证与单点登录的课程设计实现。三个服务同机部署：认证中心负责登录、会话与授权签发，教务信息服务 A、图书信息服务 B 作为独立客户端验证跨系统免密登录。

本仓库当前阶段为**第 6 周编码**，实现范围是需求清单中 F01 至 F06（负责人：李鹏）。账号管理、角色授权、应用接入与审计查询分别由 F07 至 F18 承接，尚未并入本仓库。

## 目录结构

```
sso-platform/
├── pom.xml                 聚合与依赖版本
├── db/init.sql             建库建表脚本（MySQL 8.4，utf8mb4）
├── sso-auth-server/        认证中心（9000）
│   ├── src/main/java/com/scu/sso/auth/
│   │   ├── config/         授权服务器、安全链、演示数据
│   │   ├── domain/         sys_user、auth_session、security_policy、audit_event
│   │   ├── repository/     数据访问
│   │   ├── security/       登录主体、成功与失败处理、会话有效性过滤器
│   │   ├── session/        会话时限判定、会话服务、退出处理
│   │   ├── audit/          审计写入
│   │   └── web/            登录页、个人首页、会话接口
│   └── src/main/resources/ application.yml、Thymeleaf 模板
├── app-a/                  教务信息服务 A（8081）
├── app-b/                  图书信息服务 B（8082）
└── docs/                   模块与需求对应关系
```

## 技术栈

| 项 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 与设计文档技术基线一致 |
| Spring Boot | 3.5.16 | Web、Thymeleaf、Data JPA |
| Spring Security | 6.5.x | 表单登录、会话管理、CSRF |
| Spring Authorization Server | 1.5.x | OIDC 授权码 + PKCE S256 + 令牌内省 |
| MySQL | 8.4 | 字符集 utf8mb4，时间统一 UTC |
| 构建 | Maven 3.9 | `mvn -B package` |

## 快速开始

1. 建库建表（首次执行一次，脚本本身幂等）：

   ```bash
   mysql -u root -p < db/init.sql
   ```

2. 创建应用账号并授权（示例，按本机实际情况修改）：

   ```sql
   CREATE USER 'sso'@'localhost' IDENTIFIED BY 'sso';
   GRANT ALL PRIVILEGES ON sso.* TO 'sso'@'localhost';
   ```

   连接信息可用环境变量覆盖：`SSO_DB_USER`、`SSO_DB_PASSWORD`。

3. 构建：

   ```bash
   mvn -B package
   ```

4. 启动顺序（不要颠倒，应用启动时要访问认证中心的 JWKS）：

   ```bash
   java -jar sso-auth-server/target/sso-auth-server-0.1.0.jar
   java -jar app-a/target/app-a-0.1.0.jar
   java -jar app-b/target/app-b-0.1.0.jar
   ```

5. 打开 <http://127.0.0.1:8081/>，应跳转认证中心登录页；登录后回到 A；再打开 <http://127.0.0.1:8082/> 应免密进入。

## 演示账号

首次启动时由 `DataSeeder` 幂等写入，密码统一为 `StudyPass2026!`（可用 `sso.demo.password` 覆盖）。

| 账号 | 状态 | 角色 | 用途 |
| --- | --- | --- | --- |
| admin01 | ACTIVE | ADMIN | 口令由 `sso.demo.admin-password` 指定（与演示账号口令分离），首次登录须改密 |
| alice01 | ACTIVE | USER | 正常登录与单点登录 |
| aonly01 | ACTIVE | USER | 仅访问 A 的对照账号 |
| pending01 | PENDING | 无 | 验证待审核不能登录 |
| disabled01 | DISABLED | 无 | 验证停用不能登录 |

## 本阶段实现范围

| 需求 | 内容 | 主要实现 |
| --- | --- | --- |
| F01 集中账号登录 | 表单登录、状态与锁定校验、统一失败提示、登录后建立中心会话 | `SecurityConfig`、`DbUserDetailsService`、`SsoAuthenticationSuccessHandler`、`UnifiedLoginFailureHandler` |
| F02 跨系统单点登录 | OIDC 授权码 + PKCE S256、复用中心会话、回调地址精确匹配 | `AuthorizationServerConfig`、`app-a` / `app-b` 客户端配置 |
| F03 受保护资源访问控制 | 默认拒绝、未认证 401、会话无效即拒绝、内省端点由授权服务器提供 | `SessionValidityFilter`、各客户端 `SecurityConfig` |
| F04 退出登录 | 统一退出撤销当前浏览器会话；接口支持 local / global 两种范围 | `CenterSessionLogoutHandler`、`LogoutApiController` |
| F05 会话有效期处理 | 空闲与绝对时限快照、每次访问判定、边界按大于等于 | `SessionPolicy`、`SessionService`、`SessionValidityFilter` |
| F06 用户登录会话管理 | 本人会话列表与结束指定会话，越权返回 403 | `MeSessionController` |

接口一览：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /login | 登录页 |
| POST | /login | 提交凭据 |
| POST | /logout | 统一退出（撤销本浏览器会话） |
| GET | /api/me/sessions | 本人会话列表 |
| DELETE | /api/me/sessions/{sid} | 结束指定会话 |
| POST | /api/me/logout?scope=local\|global | 接口方式退出 |
| GET | /oauth2/authorize | 授权端点 |
| POST | /oauth2/token | 换码端点 |
| POST | /oauth2/introspect | 令牌内省 |

## 关键约定

- **端口**：认证中心 9000，应用 A 8081，应用 B 8082。
- **Cookie 名**：`SESSION_CENTER`、`SESSION_A`、`SESSION_B`。浏览器 Cookie 不按端口隔离，三个服务必须使用不同名称，否则无法演示跨系统单点登录。
- **协议**：演示环境使用 HTTP，属隔离网络内的本地运行；需要 HTTPS 时由部署配置切换，代码不做区分。
- **令牌时限**：授权码 60 秒一次性、访问令牌 10 分钟、不签发刷新令牌，常量集中在 `RegisteredClientFactory`，与需求 BR-24 一致。
- **令牌时限**：授权码 60 秒一次性、访问令牌 10 分钟、不签发刷新令牌，常量集中在 `RegisteredClientFactory`，与需求 BR-24 一致。
- **客户端类型**：两个业务系统按机密客户端登记（`client_secret_basic`），密钥以散列保存，同时强制 PKCE S256。密钥通过 `SSO_APP_A_SECRET` / `SSO_APP_B_SECRET` 覆盖，演示默认值仅用于本机。
- **时间**：数据库以 UTC 存储，界面按北京时间展示；会话判定只使用服务端时间。

## 测试

```bash
mvn -B test
```

当前包含 `SessionPolicyTest`：覆盖空闲时限边界、绝对时限优先与缺字段不放行三种情形，对应需求 BR-12。

## 尚未实现（由其他成员承接）

| 需求 | 说明 |
| --- | --- |
| F07 至 F12 | 账号维护、注册审核、资料、密码、失败锁定、角色分配 |
| F13 至 F18 | 应用登记、应用授权、审计查询、统计、策略配置、异常事件 |

相关缺口已在代码中以只读方式预留：`UserRoleReader`（角色读取）、`PolicyService`（策略读取）、`AuditWriter`（审计写入）。接入时替换实现即可，不改动本阶段接口。

## 验证记录（2026-10-22）

| 项目 | 结果 |
| --- | --- |
| 编译与打包 | `mvn -B package` 通过，产出 `sso-auth-server`、`app-a`、`app-b` 三个可执行 jar |
| 单元测试 | 9 项全部通过：会话时限 4 项、会话越权 2 项、客户端登记 3 项（机密客户端、PKCE 与令牌有效期、回调精确匹配） |
| 冒烟验证（H2 内存库，仅本机验证用，不提交） | 登录页 200；未认证访问 `/api/me/sessions` 返回 401；未认证访问页面 302 至登录页；未认证调用 `/oauth2/introspect` 返回 401；授权端点直连返回 401；`/oauth2/token` 未提供客户端凭据返回 401 |
| 未覆盖 | 使用真实口令完成“登录 → 单点登录 → 退出”的端到端链路，需在 MySQL 环境按第 4 步人工执行一遍 |

验证使用的 H2 配置与建表脚本只存在于本地构建目录，未写入仓库；生产配置仍为 MySQL。

## 验证记录（2026-10-22）

| 项目 | 结果 |
| --- | --- |
| 编译与打包 | `mvn -B package` 通过，产出 `sso-auth-server`、`app-a`、`app-b` 三个可执行 jar |
| 单元测试 | 9 项全部通过：会话时限 4 项、会话越权 2 项、客户端登记 3 项（机密客户端、PKCE 与令牌有效期、回调精确匹配） |
| 冒烟验证（H2 内存库，仅本机验证用，不提交） | 登录页 200；未认证访问 `/api/me/sessions` 返回 401；未认证访问页面 302 至登录页；未认证调用 `/oauth2/introspect` 返回 401；授权端点直连返回 401；`/oauth2/token` 未提供客户端凭据返回 401 |
| 未覆盖 | 使用真实口令完成“登录 → 单点登录 → 退出”的端到端链路，需在 MySQL 环境按第 4 步人工执行一遍 |

验证使用的 H2 配置与建表脚本只存在于本地构建目录，未写入仓库；生产配置仍为 MySQL。

## 说明

本项目为四川大学软件工程课程设计作业，代码与文档同步维护；提交前请勿将本机数据库口令写入版本库。

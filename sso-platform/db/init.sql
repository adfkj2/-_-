-- 统一认证登录：演示数据库初始化（MySQL 8.4 / utf8mb4）
-- 只建表，不插入账号数据；账号由应用启动时的 DataSeeder 幂等写入。

CREATE DATABASE IF NOT EXISTS sso DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE sso;

CREATE TABLE IF NOT EXISTS sys_user (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户标识',
  username              VARCHAR(32)  NOT NULL COMMENT '登录名，小写',
  password_hash         VARCHAR(255) NOT NULL COMMENT '密码散列',
  display_name          VARCHAR(64)  NOT NULL COMMENT '显示名',
  email                 VARCHAR(254) DEFAULT NULL COMMENT '联系邮箱',
  status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/DISABLED',
  must_change_password  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '下次登录必须改密',
  auth_version          BIGINT       NOT NULL DEFAULT 0 COMMENT '认证版本，改密或停用时递增',
  failed_count          INT          NOT NULL DEFAULT 0 COMMENT '当前失败窗口计数',
  locked_until          DATETIME(6)  DEFAULT NULL COMMENT '临时锁定截止时间',
  created_at            DATETIME(6)  NOT NULL COMMENT '创建时间',
  updated_at            DATETIME(6)  NOT NULL COMMENT '修改时间',
  version               BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_status (status),
  KEY idx_user_lock (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色标识',
  code        VARCHAR(32) NOT NULL COMMENT '角色编码，大写',
  name        VARCHAR(64) NOT NULL COMMENT '角色名称',
  enabled     TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否参与授权判定',
  system_role TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '系统角色保护标志',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS user_role (
  user_id BIGINT NOT NULL COMMENT '用户',
  role_id BIGINT NOT NULL COMMENT '角色',
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系';

CREATE TABLE IF NOT EXISTS auth_session (
  sid                  VARCHAR(64)  NOT NULL COMMENT '中心会话标识',
  user_id              BIGINT       NOT NULL COMMENT '所属用户',
  http_session_digest  CHAR(64)     NOT NULL COMMENT '运行会话摘要',
  created_at           DATETIME(6)  NOT NULL COMMENT '创建时间',
  last_active_at       DATETIME(6)  NOT NULL COMMENT '最近活动时间',
  absolute_expires_at  DATETIME(6)  NOT NULL COMMENT '绝对截止时间',
  idle_timeout_seconds INT          NOT NULL COMMENT '空闲时限快照，秒',
  status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
  revoked_at           DATETIME(6)  DEFAULT NULL COMMENT '终止时间',
  revoke_reason        VARCHAR(64)  DEFAULT NULL COMMENT '终止原因编码',
  device_summary       VARCHAR(200) NOT NULL COMMENT '设备摘要，已脱敏',
  policy_version       BIGINT       NOT NULL COMMENT '创建时策略版本',
  user_auth_version    BIGINT       NOT NULL COMMENT '创建时用户认证版本',
  PRIMARY KEY (sid),
  KEY idx_session_user_status (user_id, status),
  KEY idx_session_absolute (status, absolute_expires_at),
  KEY idx_session_last_active (status, last_active_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中心会话';

CREATE TABLE IF NOT EXISTS security_policy (
  id                      BIGINT NOT NULL DEFAULT 1 COMMENT '单例记录',
  idle_timeout_minutes    INT    NOT NULL DEFAULT 30 COMMENT '空闲时限，分钟',
  absolute_timeout_hours  INT    NOT NULL DEFAULT 8  COMMENT '绝对时限，小时',
  failure_window_minutes  INT    NOT NULL DEFAULT 15 COMMENT '失败统计窗口，分钟',
  failure_threshold       INT    NOT NULL DEFAULT 5  COMMENT '锁定阈值',
  lock_minutes            INT    NOT NULL DEFAULT 15 COMMENT '临时锁定时长，分钟',
  updated_by              BIGINT DEFAULT NULL COMMENT '最后修改人',
  updated_at              DATETIME(6) NOT NULL COMMENT '修改时间',
  version                 BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  CONSTRAINT ck_policy_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证安全策略';

CREATE TABLE IF NOT EXISTS audit_event (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审计标识',
  event_key        VARCHAR(100) NOT NULL COMMENT '幂等键',
  actor_user_id    BIGINT       DEFAULT NULL COMMENT '操作者或目标用户',
  application_id   BIGINT       DEFAULT NULL COMMENT '关联应用',
  event_type       VARCHAR(48)  NOT NULL COMMENT '事件类型',
  result           VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILURE',
  occurred_at      DATETIME(6)  NOT NULL COMMENT '发生时间，UTC',
  request_id       VARCHAR(64)  NOT NULL COMMENT '请求标识',
  source_masked    VARCHAR(100) DEFAULT NULL COMMENT '来源摘要，已脱敏',
  detail_json      JSON         DEFAULT NULL COMMENT '事件详情，字段白名单',
  PRIMARY KEY (id),
  UNIQUE KEY uk_audit_event_key (event_key),
  KEY idx_audit_query (occurred_at, event_type, result),
  KEY idx_audit_user (actor_user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计记录';

-- Spring Authorization Server 1.5.x 官方 MySQL 脚本：授权记录必须落库，
-- 否则退出、会话到期和撤权都无从吊销（设计 5.9、4.2）。
CREATE TABLE IF NOT EXISTS oauth2_authorization (
  id                            VARCHAR(100)  NOT NULL COMMENT '授权记录标识',
  registered_client_id          VARCHAR(100)  NOT NULL COMMENT '客户端标识',
  principal_name                VARCHAR(200)  NOT NULL COMMENT '主体名',
  authorization_grant_type      VARCHAR(100)  NOT NULL COMMENT '授权类型',
  authorized_scopes             VARCHAR(1000) DEFAULT NULL COMMENT '已授权范围',
  attributes                    BLOB          DEFAULT NULL COMMENT '授权属性',
  state                         VARCHAR(500)  DEFAULT NULL COMMENT 'state',
  authorization_code_value      BLOB          DEFAULT NULL COMMENT '授权码',
  authorization_code_issued_at  TIMESTAMP     NULL DEFAULT NULL,
  authorization_code_expires_at TIMESTAMP     NULL DEFAULT NULL,
  authorization_code_metadata   BLOB          DEFAULT NULL,
  access_token_value            BLOB          DEFAULT NULL COMMENT '不透明访问令牌',
  access_token_issued_at        TIMESTAMP     NULL DEFAULT NULL,
  access_token_expires_at       TIMESTAMP     NULL DEFAULT NULL,
  access_token_metadata         BLOB          DEFAULT NULL,
  access_token_type             VARCHAR(100)  DEFAULT NULL,
  access_token_scopes           VARCHAR(1000) DEFAULT NULL,
  oidc_id_token_value           BLOB          DEFAULT NULL,
  oidc_id_token_issued_at       TIMESTAMP     NULL DEFAULT NULL,
  oidc_id_token_expires_at      TIMESTAMP     NULL DEFAULT NULL,
  oidc_id_token_metadata        BLOB          DEFAULT NULL,
  refresh_token_value           BLOB          DEFAULT NULL,
  refresh_token_issued_at       TIMESTAMP     NULL DEFAULT NULL,
  refresh_token_expires_at      TIMESTAMP     NULL DEFAULT NULL,
  refresh_token_metadata        BLOB          DEFAULT NULL,
  user_code_value               BLOB          DEFAULT NULL,
  user_code_issued_at           TIMESTAMP     NULL DEFAULT NULL,
  user_code_expires_at          TIMESTAMP     NULL DEFAULT NULL,
  user_code_metadata            BLOB          DEFAULT NULL,
  device_code_value             BLOB          DEFAULT NULL,
  device_code_issued_at         TIMESTAMP     NULL DEFAULT NULL,
  device_code_expires_at        TIMESTAMP     NULL DEFAULT NULL,
  device_code_metadata          BLOB          DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 授权记录';

-- 授权记录与中心会话的关联：退出、到期、撤权时按 sid 反查并吊销授权（设计 4.2）。
CREATE TABLE IF NOT EXISTS authorization_session_link (
  authorization_id VARCHAR(100) NOT NULL COMMENT '授权记录标识',
  sid              VARCHAR(64)  NOT NULL COMMENT '中心会话标识',
  user_id          BIGINT       NOT NULL COMMENT '所属用户',
  created_at       DATETIME(6)  NOT NULL COMMENT '建立时间',
  PRIMARY KEY (authorization_id),
  KEY idx_link_sid (sid),
  KEY idx_link_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权与中心会话关联';

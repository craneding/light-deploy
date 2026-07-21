SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 初始化数据库结构 (MySQL)

CREATE DATABASE IF NOT EXISTS light_deploy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE light_deploy;

-- ----------------------------
-- 1. 用户表 (GitLab OAuth 登录)
-- ----------------------------
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    gitlab_id INT NOT NULL UNIQUE COMMENT 'GitLab 用户 ID',
    username VARCHAR(255) NOT NULL COMMENT 'GitLab 用户名',
    email VARCHAR(255) NOT NULL COMMENT '用户邮箱',
    avatar_url VARCHAR(512) COMMENT '用户头像 URL',
    access_token VARCHAR(512) COMMENT 'GitLab Access Token',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2. 项目表
-- ----------------------------
CREATE TABLE IF NOT EXISTS projects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    gitlab_project_id INT NOT NULL COMMENT 'GitLab 项目 ID',
    name VARCHAR(255) NOT NULL COMMENT '项目名称',
    build_script TEXT COMMENT '构建脚本',
    build_output_dir VARCHAR(255) COMMENT '构建后产物目录',
    deploy_dir VARCHAR(255) COMMENT '部署目录',
    sync_to_deploy_dir TINYINT(1) DEFAULT 0 COMMENT '是否同步到部署目录',
    pre_script TEXT COMMENT '前置脚本',
    post_script TEXT COMMENT '后置脚本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- ----------------------------
-- 3. 服务器表
-- ----------------------------
CREATE TABLE IF NOT EXISTS servers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '服务器名称',
    ip VARCHAR(50) NOT NULL COMMENT 'IP 地址',
    port INT DEFAULT 22 COMMENT 'SSH 端口',
    username VARCHAR(100) DEFAULT 'root' COMMENT 'SSH 用户',
    password VARCHAR(255) COMMENT 'SSH 密码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器表';

-- ----------------------------
-- 新增: 部署 Profile 表
-- ----------------------------
CREATE TABLE IF NOT EXISTS deploy_profiles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL COMMENT '关联项目 ID',
    server_id INT COMMENT '关联服务器 ID',
    name VARCHAR(255) NOT NULL COMMENT 'Profile 名称 (如: 生产环境, 测试环境)',
    build_script TEXT COMMENT '构建脚本',
    build_output_dir VARCHAR(255) COMMENT '构建后产物目录',
    deploy_dir VARCHAR(255) COMMENT '部署目录',
    sync_to_deploy_dir TINYINT(1) DEFAULT 0 COMMENT '是否同步到部署目录',
    pre_script TEXT COMMENT '前置脚本',
    post_script TEXT COMMENT '后置脚本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目部署 Profile 表';

-- ----------------------------
-- 4. 部署任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS deploy_tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    project_id INT NOT NULL COMMENT '关联项目 ID',
    profile_id INT NOT NULL COMMENT '关联 Profile ID',
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (profile_id) REFERENCES deploy_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署任务配置表';

-- ----------------------------
-- 5. 部署记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS deploy_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL COMMENT '关联部署任务 ID',
    trigger_user_id INT COMMENT '触发部署的用户 ID',
    status VARCHAR(50) DEFAULT 'PENDING' COMMENT '当前部署状态',
    branch VARCHAR(100) COMMENT '部署分支',
    commit_id VARCHAR(100) COMMENT '部署的 Commit Hash',
    logs LONGTEXT COMMENT '部署日志输出内容',
    start_time TIMESTAMP NULL COMMENT '开始时间',
    end_time TIMESTAMP NULL COMMENT '结束时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (task_id) REFERENCES deploy_tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (trigger_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署执行记录表';

-- ====================================================================
-- 知识库系统数据库迁移脚本（MySQL 8+）
-- 版本: 1.0
-- 日期: 2025-01-10
-- 说明: 添加共享知识库和私人知识库支持，适配MySQL
-- ====================================================================

START TRANSACTION;

-- ====================================================================
-- 1. 创建知识库表
-- ====================================================================

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id VARCHAR(64) NOT NULL COMMENT '知识库唯一标识',
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description TEXT COMMENT '知识库描述',
    type VARCHAR(20) NOT NULL COMMENT '知识库类型：SHARED 或 PRIVATE',
    owner_id VARCHAR(64) COMMENT '所有者ID（仅PRIVATE类型）',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否激活（软删除标记）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT pk_knowledge_bases PRIMARY KEY (id),
    CONSTRAINT fk_kb_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_kb_type CHECK (type IN ('SHARED', 'PRIVATE')),
    CONSTRAINT chk_kb_owner CHECK (
        (type = 'SHARED' AND owner_id IS NULL) OR
        (type = 'PRIVATE' AND owner_id IS NOT NULL)
    ),
    INDEX idx_kb_owner (owner_id),
    INDEX idx_kb_type (type),
    INDEX idx_kb_active (is_active),
    INDEX idx_kb_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库定义表';

-- ====================================================================
-- 2. 创建知识库访问权限表
-- ====================================================================

CREATE TABLE IF NOT EXISTS user_knowledge_base_access (
    id VARCHAR(64) NOT NULL COMMENT '主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    kb_id VARCHAR(64) NOT NULL COMMENT '知识库ID',
    role VARCHAR(20) DEFAULT 'READER' COMMENT '权限角色：ADMIN、WRITER、READER',
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    granted_by VARCHAR(64) COMMENT '授权者ID',
    CONSTRAINT pk_user_kb_access PRIMARY KEY (id),
    CONSTRAINT fk_access_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_access_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    CONSTRAINT chk_access_role CHECK (role IN ('ADMIN', 'WRITER', 'READER')),
    UNIQUE KEY uk_user_kb (user_id, kb_id),
    INDEX idx_access_user (user_id),
    INDEX idx_access_kb (kb_id),
    INDEX idx_access_granted (granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户知识库访问权限表';

-- ====================================================================
-- 3. 修改现有表：uploaded_files 添加知识库关联
-- ====================================================================

-- 条件添加 kb_id 列
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'uploaded_files'
      AND column_name = 'kb_id'
);
SET @sql_add_col := IF(
    @col_exists = 0,
    'ALTER TABLE uploaded_files ADD COLUMN kb_id VARCHAR(64) COMMENT ''所属知识库ID''',
    'SELECT 1'
);
PREPARE stmt_add_col FROM @sql_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- 条件添加外键
SET @fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = 'fk_file_kb'
      AND TABLE_NAME = 'uploaded_files'
);
SET @sql := IF(
    @fk_exists = 0,
    'ALTER TABLE uploaded_files ADD CONSTRAINT fk_file_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 条件添加索引
SET @idx_files_kb := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uploaded_files'
      AND index_name = 'idx_files_kb'
);
SET @sql_idx := IF(
    @idx_files_kb = 0,
    'CREATE INDEX idx_files_kb ON uploaded_files(kb_id)',
    'SELECT 1'
);
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

-- ====================================================================
-- 4. 创建默认共享知识库
-- ====================================================================

INSERT IGNORE INTO knowledge_bases (id, name, description, type, owner_id, is_active)
VALUES (
    'kb_shared_cpp_tutorial',
    'C++教学官方知识库',
    '重庆大学大数据与软件学院C++课程官方教学资料',
    'SHARED',
    NULL,
    TRUE
);

-- ====================================================================
-- 5. 数据迁移：为现有用户创建默认私人知识库
-- ====================================================================

INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
SELECT
    CONCAT('kb_private_', u.id) AS kb_id,
    CONCAT(u.username, '的私人知识库') AS name,
    '用户个人学习资料和笔记' AS description,
    'PRIVATE' AS type,
    u.id AS owner_id,
    TRUE AS is_active
FROM users u
LEFT JOIN knowledge_bases kb
    ON kb.owner_id = u.id AND kb.type = 'PRIVATE'
WHERE kb.id IS NULL;

UPDATE uploaded_files uf
SET kb_id = CONCAT('kb_private_', uf.user_id)
WHERE kb_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM knowledge_bases kb
      WHERE kb.id = CONCAT('kb_private_', uf.user_id)
  );

-- ====================================================================
-- 6. 创建辅助视图
-- ====================================================================

CREATE OR REPLACE VIEW v_user_accessible_knowledge_bases AS
SELECT
    u.id AS user_id,
    kb.id AS kb_id,
    kb.name,
    kb.description,
    kb.type,
    kb.owner_id,
    kb.is_active,
    CASE
        WHEN kb.type = 'SHARED' THEN 'READER'
        WHEN kb.owner_id = u.id THEN 'ADMIN'
        ELSE COALESCE(acc.role, 'NONE')
    END AS effective_role
FROM users u
CROSS JOIN knowledge_bases kb
LEFT JOIN user_knowledge_base_access acc
    ON acc.user_id = u.id AND acc.kb_id = kb.id
WHERE kb.is_active = TRUE
  AND (
      kb.type = 'SHARED'
      OR kb.owner_id = u.id
      OR acc.role IS NOT NULL
  );

COMMIT;

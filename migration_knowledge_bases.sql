-- ====================================================================
-- 知识库系统数据库迁移脚本
-- 版本: 1.0
-- 日期: 2025-01-10
-- 说明: 添加共享知识库和私人知识库支持
-- ====================================================================

BEGIN;

-- ====================================================================
-- 1. 创建知识库表
-- ====================================================================

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,  -- 'SHARED' 或 'PRIVATE'
    owner_id VARCHAR(64),       -- SHARED为NULL，PRIVATE为用户ID
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_kb_owner FOREIGN KEY (owner_id)
        REFERENCES users(id) ON DELETE CASCADE,

    -- 类型检查
    CONSTRAINT chk_kb_type CHECK (type IN ('SHARED', 'PRIVATE')),

    -- 业务规则约束：共享知识库无所有者，私人知识库必须有所有者
    CONSTRAINT chk_kb_owner CHECK (
        (type = 'SHARED' AND owner_id IS NULL) OR
        (type = 'PRIVATE' AND owner_id IS NOT NULL)
    )
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_kb_owner ON knowledge_bases(owner_id);
CREATE INDEX IF NOT EXISTS idx_kb_type ON knowledge_bases(type);
CREATE INDEX IF NOT EXISTS idx_kb_active ON knowledge_bases(is_active);
CREATE INDEX IF NOT EXISTS idx_kb_created ON knowledge_bases(created_at DESC);

-- 添加注释
COMMENT ON TABLE knowledge_bases IS '知识库定义表';
COMMENT ON COLUMN knowledge_bases.id IS '知识库唯一标识';
COMMENT ON COLUMN knowledge_bases.name IS '知识库名称';
COMMENT ON COLUMN knowledge_bases.description IS '知识库描述';
COMMENT ON COLUMN knowledge_bases.type IS '知识库类型：SHARED(共享) 或 PRIVATE(私人)';
COMMENT ON COLUMN knowledge_bases.owner_id IS '所有者ID（仅PRIVATE类型）';
COMMENT ON COLUMN knowledge_bases.is_active IS '是否激活（软删除标记）';

-- ====================================================================
-- 2. 创建知识库访问权限表
-- ====================================================================

CREATE TABLE IF NOT EXISTS user_knowledge_base_access (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) DEFAULT 'READER',  -- 'ADMIN', 'WRITER', 'READER'
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64),  -- 授权者ID

    -- 外键约束
    CONSTRAINT fk_access_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_access_kb FOREIGN KEY (kb_id)
        REFERENCES knowledge_bases(id) ON DELETE CASCADE,

    -- 角色检查
    CONSTRAINT chk_access_role CHECK (role IN ('ADMIN', 'WRITER', 'READER')),

    -- 唯一约束：一个用户对一个知识库只能有一条权限记录
    UNIQUE(user_id, kb_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_access_user ON user_knowledge_base_access(user_id);
CREATE INDEX IF NOT EXISTS idx_access_kb ON user_knowledge_base_access(kb_id);
CREATE INDEX IF NOT EXISTS idx_access_granted ON user_knowledge_base_access(granted_at DESC);

-- 添加注释
COMMENT ON TABLE user_knowledge_base_access IS '用户知识库访问权限表';
COMMENT ON COLUMN user_knowledge_base_access.role IS '权限角色：ADMIN(管理员)、WRITER(写入者)、READER(读取者)';
COMMENT ON COLUMN user_knowledge_base_access.granted_by IS '授权者用户ID';

-- ====================================================================
-- 3. 修改现有表：uploaded_files 添加知识库关联
-- ====================================================================

-- 添加知识库ID列
ALTER TABLE uploaded_files
    ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64);

-- 添加外键约束
ALTER TABLE uploaded_files
    ADD CONSTRAINT fk_file_kb
        FOREIGN KEY (kb_id)
        REFERENCES knowledge_bases(id)
        ON DELETE CASCADE;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_files_kb ON uploaded_files(kb_id);

-- 添加注释
COMMENT ON COLUMN uploaded_files.kb_id IS '所属知识库ID';

-- ====================================================================
-- 4. 修改现有表：document_chunks 添加知识库关联（可选）
-- ====================================================================
-- 注意：当前系统主要使用Redis存储chunks，此表可能未实际使用
-- 如果未来需要PostgreSQL作为备份存储，取消以下注释

-- ALTER TABLE document_chunks
--     ADD COLUMN IF NOT EXISTS kb_id VARCHAR(64);
--
-- ALTER TABLE document_chunks
--     ADD CONSTRAINT fk_chunk_kb
--         FOREIGN KEY (kb_id)
--         REFERENCES knowledge_bases(id)
--         ON DELETE CASCADE;
--
-- CREATE INDEX IF NOT EXISTS idx_chunks_kb ON document_chunks(kb_id);
--
-- COMMENT ON COLUMN document_chunks.kb_id IS '所属知识库ID';

-- ====================================================================
-- 5. 创建默认共享知识库
-- ====================================================================

INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
VALUES (
    'kb_shared_cpp_tutorial',
    'C++教学官方知识库',
    '重庆大学大数据与软件学院C++课程官方教学资料',
    'SHARED',
    NULL,
    TRUE
)
ON CONFLICT (id) DO NOTHING;

-- ====================================================================
-- 6. 数据迁移：为现有用户创建默认私人知识库
-- ====================================================================

-- 为每个现有用户创建私人知识库
INSERT INTO knowledge_bases (id, name, description, type, owner_id, is_active)
SELECT
    'kb_private_' || id AS kb_id,
    username || '的私人知识库' AS name,
    '用户个人学习资料和笔记' AS description,
    'PRIVATE' AS type,
    id AS owner_id,
    TRUE AS is_active
FROM users
WHERE NOT EXISTS (
    SELECT 1
    FROM knowledge_bases kb
    WHERE kb.owner_id = users.id AND kb.type = 'PRIVATE'
)
ON CONFLICT (id) DO NOTHING;

-- 将现有文件关联到用户的私人知识库
UPDATE uploaded_files
SET kb_id = 'kb_private_' || user_id
WHERE kb_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM knowledge_bases kb
      WHERE kb.id = 'kb_private_' || uploaded_files.user_id
  );

-- ====================================================================
-- 7. 创建辅助视图（可选）
-- ====================================================================

-- 用户可访问知识库视图
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
      kb.type = 'SHARED'  -- 所有用户可访问共享知识库
      OR kb.owner_id = u.id  -- 用户可访问自己的私人知识库
      OR acc.id IS NOT NULL  -- 用户有显式授权
  );

COMMENT ON VIEW v_user_accessible_knowledge_bases IS '用户可访问的知识库视图（含有效权限）';

-- ====================================================================
-- 8. 创建有用的查询函数（可选）
-- ====================================================================

-- 检查用户对知识库的访问权限
CREATE OR REPLACE FUNCTION check_kb_access(
    p_user_id VARCHAR(64),
    p_kb_id VARCHAR(64),
    p_min_role VARCHAR(20) DEFAULT 'READER'
) RETURNS BOOLEAN AS $$
DECLARE
    v_kb_type VARCHAR(20);
    v_owner_id VARCHAR(64);
    v_user_role VARCHAR(20);
    v_role_level INT;
    v_min_level INT;
BEGIN
    -- 获取知识库信息
    SELECT type, owner_id INTO v_kb_type, v_owner_id
    FROM knowledge_bases
    WHERE id = p_kb_id AND is_active = TRUE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    -- 确定用户角色
    IF v_kb_type = 'SHARED' THEN
        v_user_role := COALESCE(
            (SELECT role FROM user_knowledge_base_access
             WHERE user_id = p_user_id AND kb_id = p_kb_id),
            'READER'
        );
    ELSIF v_owner_id = p_user_id THEN
        v_user_role := 'ADMIN';
    ELSE
        SELECT role INTO v_user_role
        FROM user_knowledge_base_access
        WHERE user_id = p_user_id AND kb_id = p_kb_id;

        IF NOT FOUND THEN
            RETURN FALSE;
        END IF;
    END IF;

    -- 角色等级转换
    v_role_level := CASE v_user_role
        WHEN 'ADMIN' THEN 3
        WHEN 'WRITER' THEN 2
        WHEN 'READER' THEN 1
        ELSE 0
    END;

    v_min_level := CASE p_min_role
        WHEN 'ADMIN' THEN 3
        WHEN 'WRITER' THEN 2
        WHEN 'READER' THEN 1
        ELSE 0
    END;

    RETURN v_role_level >= v_min_level;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_kb_access IS '检查用户对知识库的访问权限';

-- ====================================================================
-- 9. 创建触发器：自动更新 updated_at
-- ====================================================================

CREATE OR REPLACE FUNCTION update_knowledge_base_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_kb_update_timestamp
    BEFORE UPDATE ON knowledge_bases
    FOR EACH ROW
    EXECUTE FUNCTION update_knowledge_base_timestamp();

-- ====================================================================
-- 10. 验证数据完整性
-- ====================================================================

-- 检查是否有文件未关联到知识库
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM uploaded_files
    WHERE kb_id IS NULL;

    IF orphan_count > 0 THEN
        RAISE WARNING '发现 % 个文件未关联到知识库，请手动处理', orphan_count;
    ELSE
        RAISE NOTICE '所有文件已正确关联到知识库';
    END IF;
END $$;

-- 检查知识库约束
DO $$
DECLARE
    invalid_kb_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_kb_count
    FROM knowledge_bases
    WHERE (type = 'SHARED' AND owner_id IS NOT NULL)
       OR (type = 'PRIVATE' AND owner_id IS NULL);

    IF invalid_kb_count > 0 THEN
        RAISE EXCEPTION '发现 % 个知识库违反类型约束', invalid_kb_count;
    ELSE
        RAISE NOTICE '所有知识库类型约束检查通过';
    END IF;
END $$;

COMMIT;

-- ====================================================================
-- 迁移完成提示
-- ====================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '知识库系统迁移成功完成！';
    RAISE NOTICE '========================================';
    RAISE NOTICE '已创建表：';
    RAISE NOTICE '  - knowledge_bases';
    RAISE NOTICE '  - user_knowledge_base_access';
    RAISE NOTICE '已创建视图：';
    RAISE NOTICE '  - v_user_accessible_knowledge_bases';
    RAISE NOTICE '已创建函数：';
    RAISE NOTICE '  - check_kb_access()';
    RAISE NOTICE '========================================';
    RAISE NOTICE '下一步：';
    RAISE NOTICE '  1. 执行 Redis 数据迁移脚本';
    RAISE NOTICE '  2. 部署新版本后端代码';
    RAISE NOTICE '  3. 验证现有功能正常工作';
    RAISE NOTICE '========================================';
END $$;
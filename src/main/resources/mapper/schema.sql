-- 基础表结构（MySQL 8+）

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active TINYINT(1) DEFAULT 1,
    last_login TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    token VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_revoked TINYINT(1) DEFAULT 0,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 兼容低版本MySQL，使用条件创建索引
SET @idx_refresh_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_tokens'
      AND index_name = 'idx_refresh_tokens_user'
);
SET @sql_idx_refresh := IF(
    @idx_refresh_exists = 0,
    'CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id)',
    'SELECT 1'
);
PREPARE stmt_idx_refresh FROM @sql_idx_refresh;
EXECUTE stmt_idx_refresh;
DEALLOCATE PREPARE stmt_idx_refresh;

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,
    owner_id VARCHAR(64),
    is_active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_knowledge_base_access (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) DEFAULT 'READER',
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64),
    CONSTRAINT fk_access_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_access_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    CONSTRAINT chk_access_role CHECK (role IN ('ADMIN', 'WRITER', 'READER')),
    UNIQUE KEY uk_user_kb (user_id, kb_id),
    INDEX idx_access_user (user_id),
    INDEX idx_access_kb (kb_id),
    INDEX idx_access_granted (granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS uploaded_files (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    filename TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(32),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) DEFAULT 'PROCESSING',
    kb_id VARCHAR(64),
    CONSTRAINT fk_uploaded_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_file_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    CONSTRAINT chk_uploaded_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET @idx_upload_user := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uploaded_files' AND index_name = 'idx_uploaded_files_user'
);
SET @sql_idx_upload_user := IF(
    @idx_upload_user = 0,
    'CREATE INDEX idx_uploaded_files_user ON uploaded_files(user_id)',
    'SELECT 1'
);
PREPARE stmt_idx_upload_user FROM @sql_idx_upload_user;
EXECUTE stmt_idx_upload_user;
DEALLOCATE PREPARE stmt_idx_upload_user;

SET @idx_upload_kb := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'uploaded_files' AND index_name = 'idx_uploaded_files_kb'
);
SET @sql_idx_upload_kb := IF(
    @idx_upload_kb = 0,
    'CREATE INDEX idx_uploaded_files_kb ON uploaded_files(kb_id)',
    'SELECT 1'
);
PREPARE stmt_idx_upload_kb FROM @sql_idx_upload_kb;
EXECUTE stmt_idx_upload_kb;
DEALLOCATE PREPARE stmt_idx_upload_kb;

CREATE TABLE IF NOT EXISTS document_chunks (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    file_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64),
    chunk_index INT NOT NULL,
    content TEXT,
    embedding JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id) REFERENCES uploaded_files (id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx_chunk_user := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'document_chunks' AND index_name = 'idx_document_chunks_user'
);
SET @sql_idx_chunk_user := IF(
    @idx_chunk_user = 0,
    'CREATE INDEX idx_document_chunks_user ON document_chunks(user_id)',
    'SELECT 1'
);
PREPARE stmt_idx_chunk_user FROM @sql_idx_chunk_user;
EXECUTE stmt_idx_chunk_user;
DEALLOCATE PREPARE stmt_idx_chunk_user;

SET @idx_chunk_file := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'document_chunks' AND index_name = 'idx_document_chunks_file'
);
SET @sql_idx_chunk_file := IF(
    @idx_chunk_file = 0,
    'CREATE INDEX idx_document_chunks_file ON document_chunks(file_id)',
    'SELECT 1'
);
PREPARE stmt_idx_chunk_file FROM @sql_idx_chunk_file;
EXECUTE stmt_idx_chunk_file;
DEALLOCATE PREPARE stmt_idx_chunk_file;

SET @idx_chunk_kb := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'document_chunks' AND index_name = 'idx_document_chunks_kb'
);
SET @sql_idx_chunk_kb := IF(
    @idx_chunk_kb = 0,
    'CREATE INDEX idx_document_chunks_kb ON document_chunks(kb_id)',
    'SELECT 1'
);
PREPARE stmt_idx_chunk_kb FROM @sql_idx_chunk_kb;
EXECUTE stmt_idx_chunk_kb;
DEALLOCATE PREPARE stmt_idx_chunk_kb;

CREATE TABLE IF NOT EXISTS chat_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    first_message TEXT,
    model VARCHAR(100),
    message_count INT DEFAULT 0,
    last_message_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_session_user (user_id),
    INDEX idx_chat_session_last (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content MEDIUMTEXT,
    seq INT NOT NULL,
    model VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_msg_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_msg_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_msg_session (session_id, seq),
    INDEX idx_chat_msg_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

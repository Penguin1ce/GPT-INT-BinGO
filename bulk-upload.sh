#!/bin/bash

# 批量上传文件到公共知识库
# 使用示例:
#   ./bulk-upload.sh /path/to/files admin kb_shared_cpp_tutorial
#   ./bulk-upload.sh /path/to/files admin kb_shared_cpp_tutorial 300 true

set -e

# 默认参数
DEFAULT_USER="admin"
DEFAULT_KB="kb_shared_cpp_tutorial"
DEFAULT_WAIT_SECONDS=180
DEFAULT_RETRY="true"

# 获取参数
DIR="${1}"
USER="${2:-$DEFAULT_USER}"
KB="${3:-$DEFAULT_KB}"
WAIT_SECONDS="${4:-$DEFAULT_WAIT_SECONDS}"
RETRY_FAILED="${5:-$DEFAULT_RETRY}"

# 参数验证
if [ -z "$DIR" ]; then
    echo "错误: 必须提供文件目录路径"
    echo ""
    echo "使用方法:"
    echo "  $0 <目录路径> [用户名] [知识库ID] [等待秒数] [失败重试]"
    echo ""
    echo "参数说明:"
    echo "  目录路径      - 要导入的文件目录绝对路径 (必填)"
    echo "  用户名        - 已有用户名 (默认: admin)"
    echo "  知识库ID      - 目标知识库ID (默认: kb_shared_cpp_tutorial)"
    echo "  等待秒数      - 轮询索引完成的超时时间 (默认: 180)"
    echo "  失败重试      - 失败时是否自动重试一次 (默认: true)"
    echo ""
    echo "示例:"
    echo "  $0 /path/to/public_files"
    echo "  $0 /path/to/public_files admin kb_custom 300 true"
    exit 1
fi

if [ ! -d "$DIR" ]; then
    echo "错误: 目录不存在: $DIR"
    exit 1
fi

# 构建Maven命令参数
EXEC_ARGS="dir=$DIR user=$USER kb=$KB waitSeconds=$WAIT_SECONDS retryFailedOnce=$RETRY_FAILED"

echo "=========================================="
echo "批量上传公共知识库"
echo "=========================================="
echo "目录路径:    $DIR"
echo "用户名:      $USER"
echo "知识库ID:    $KB"
echo "等待秒数:    $WAIT_SECONDS"
echo "失败重试:    $RETRY_FAILED"
echo "=========================================="
echo ""

# 执行Maven命令
mvn -q -DskipTests clean compile exec:java \
    -Dexec.mainClass=com.firefly.ragdemo.tool.BulkPublicKbUploader \
    -Dspring.profiles.active=dev \
    -Dexec.args="$EXEC_ARGS"

echo ""
echo "批量上传完成"

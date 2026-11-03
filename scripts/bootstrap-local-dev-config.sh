#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
ADMIN_FRONTEND_ENV_FILE=${ACQUIRING_ADMIN_DEV_ENV_FILE:-"${REPOSITORY_ROOT}/../acquiring-frontend/apps/admin-system/.env.development.local"}

ADMIN_LOGIN_ACCOUNT=${ADMIN_DEFAULT_LOGIN_ACCOUNT:-admin}
ADMIN_LOGIN_PASSWORD=${ADMIN_DEFAULT_LOGIN_PASSWORD:-Admin@123456}

umask 077

fail() {
    printf '%s\n' "本地开发配置初始化失败: $1" >&2
    exit 1
}

property_value() {
    awk -F= -v expected_key="$2" '
        $1 == expected_key {
            sub(/^[^=]*=/, "")
            print
            exit
        }
    ' "$1"
}

require_property() {
    value=$(property_value "$1" "$2")
    [ -n "${value}" ] || fail "配置文件 $1 缺少属性 $2"
}

require_minimum_length() {
    value=$(property_value "$1" "$2")
    [ "${#value}" -ge "$3" ] || fail "配置文件 $1 的属性 $2 长度不足"
}

validate_frontend_env_file() {
    require_property "$ADMIN_FRONTEND_ENV_FILE" "VITE_ADMIN_DEV_LOGIN_ASSIST"
    require_property "$ADMIN_FRONTEND_ENV_FILE" "VITE_ADMIN_DEV_LOGIN_ACCOUNT"
    require_minimum_length "$ADMIN_FRONTEND_ENV_FILE" "VITE_ADMIN_DEV_LOGIN_PASSWORD" 12
}

create_admin_frontend_env_file() {
    [ ! -e "$ADMIN_FRONTEND_ENV_FILE" ] || return 0
    frontend_directory=$(dirname -- "$ADMIN_FRONTEND_ENV_FILE")
    [ -d "$frontend_directory" ] || return 0
    temporary_file=$(mktemp "${frontend_directory}/.env.development.local.XXXXXX")
    {
        printf '%s\n' "# 本文件由 acquiring-orchestration/scripts/bootstrap-local-dev-config.sh 生成，仅供本机开发。"
        printf '%s\n' "VITE_ADMIN_DEV_LOGIN_ASSIST=true"
        printf '%s\n' "VITE_ADMIN_DEV_LOGIN_ACCOUNT=${ADMIN_LOGIN_ACCOUNT}"
        printf '%s\n' "VITE_ADMIN_DEV_LOGIN_PASSWORD=${ADMIN_LOGIN_PASSWORD}"
    } >"$temporary_file"
    chmod 600 "$temporary_file"
    mv "$temporary_file" "$ADMIN_FRONTEND_ENV_FILE"
}

create_admin_frontend_env_file
if [ -e "$ADMIN_FRONTEND_ENV_FILE" ]; then
    validate_frontend_env_file
    printf '%s\n' "Admin 前端登录辅助配置已就绪: ${ADMIN_FRONTEND_ENV_FILE}"
else
    printf '%s\n' "未检测到 Admin 前端目录，已跳过登录辅助配置。"
fi
printf '%s\n' "后端 Risk、MFA 和 Merchant 默认登录配置由 Nacos 服务专属 DataId 管理。"

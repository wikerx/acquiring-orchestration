#!/usr/bin/env bash

# 密钥管理命令不得继承调用方的 xtrace，避免 access token 和 HMAC 密钥进入终端或 CI 日志。
set +x
set -euo pipefail

# Nacos 服务级配置管理工具。每个服务只加载既有 service-xxx-{env}.yaml，
# 服务业务配置与其参与的内部调用边密钥保存在同一 DataId，避免新增内部鉴权专用配置。
readonly NACOS_ENDPOINT="${NACOS_ENDPOINT:-http://127.0.0.1:8848/nacos}"
readonly NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
readonly SECRET_BYTES=48
readonly DEFAULT_DEV_NAMESPACE="324ad8dc-58d0-4d0d-b264-24a9f951a2b0"
readonly AUTH_BUNDLE_MARKER="# --- managed internal-auth bundle; rotate secrets through this script ---"
readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SERVICE_CONFIG_TEMPLATE_DIR="${SERVICE_CONFIG_TEMPLATE_DIR:-${REPOSITORY_ROOT}/docs/deployment/nacos}"
readonly SERVICE_CONFIG_BACKUP_ROOT="${SERVICE_CONFIG_BACKUP_ROOT:-${REPOSITORY_ROOT}/runtime/nacos-backups}"
readonly SERVICES=(
  service-admin
  service-checkout
  service-clearing
  service-data
  service-gateway
  service-job
  service-merchant
  service-openapi
  service-payment
  service-payout
  service-risk
  service-settlement
)
readonly EDGES=(
  merchant-admin
  admin-job
  admin-payment
  admin-clearing
  admin-settlement
  job-payment
  job-clearing
  job-data
  merchant-payment
  openapi-payment
  openapi-payout
  payment-risk
  gateway-checkout
)

work_dir=""
access_token=""

usage() {
  printf '%s\n' \
    'Usage:' \
    '  manage-nacos-internal-auth.sh init-container-env <absolute-env-file>' \
    '  manage-nacos-internal-auth.sh migrate <env> <namespace-id>' \
    '  manage-nacos-internal-auth.sh verify <env> <namespace-id>' \
    '  manage-nacos-internal-auth.sh plan-service-configs <env> <namespace-id>' \
    '  CONFIRM_PUBLISH_SERVICE_CONFIGS=YES manage-nacos-internal-auth.sh sync-service-configs <env> <namespace-id>' \
    '  CONFIRM_RESTORE_SERVICE_CONFIGS=YES manage-nacos-internal-auth.sh restore-service-configs <env> <namespace-id> <absolute-backup-dir>' \
    '  manage-nacos-internal-auth.sh rotate-prepare <env> <namespace-id> <edge>' \
    '  manage-nacos-internal-auth.sh rotate-activate <env> <namespace-id> <edge>' \
    '  manage-nacos-internal-auth.sh retire-previous <env> <namespace-id> <edge>' \
    '  CONFIRM_DELETE_LEGACY_CONFIGS=YES manage-nacos-internal-auth.sh cleanup-legacy <env> <namespace-id>' \
    '' \
    'Environment:' \
    '  NACOS_ENDPOINT  default: http://127.0.0.1:8848/nacos' \
    '  NACOS_GROUP     default: DEFAULT_GROUP' \
    '  NACOS_USERNAME  required when Nacos auth is enabled' \
    '  NACOS_PASSWORD  required when Nacos auth is enabled' \
    '  SERVICE_CONFIG_TEMPLATE_DIR  default: docs/deployment/nacos under repository root' \
    '  SERVICE_CONFIG_BACKUP_ROOT   default: runtime/nacos-backups under repository root' \
    '' \
    "Dev namespace default documented by this repository: ${DEFAULT_DEV_NAMESPACE}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command is unavailable: %s\n' "$1" >&2
    exit 1
  fi
}

require_environment() {
  case "$1" in
    dev|test|uat|prod|sample) ;;
    *)
      printf 'Unsupported environment: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

require_namespace() {
  if [[ -z "$1" || "$1" == "public" ]]; then
    printf 'A non-public Nacos namespace id is required.\n' >&2
    exit 1
  fi
}

require_edge() {
  local requested="$1"
  local edge
  for edge in "${EDGES[@]}"; do
    if [[ "$edge" == "$requested" ]]; then
      return 0
    fi
  done
  printf 'Unsupported internal-auth edge: %s\n' "$requested" >&2
  exit 1
}

random_base64() {
  openssl rand -base64 "$1" | tr -d '\r\n'
}

random_hex() {
  openssl rand -hex "$1" | tr -d '\r\n'
}

initialize_container_env() {
  local target="$1"
  if [[ "$target" != /* ]]; then
    printf 'Container env path must be absolute.\n' >&2
    exit 1
  fi
  if [[ -e "$target" ]]; then
    printf 'Refusing to overwrite existing runtime env file: %s\n' "$target" >&2
    exit 1
  fi
  mkdir -p "$(dirname "$target")"
  umask 077
  {
    printf 'NACOS_AUTH_ENABLE=true\n'
    printf 'NACOS_AUTH_TOKEN=%s\n' "$(random_base64 32)"
    printf 'NACOS_AUTH_IDENTITY_KEY=%s\n' "$(random_hex 16)"
    printf 'NACOS_AUTH_IDENTITY_VALUE=%s\n' "$(random_hex 32)"
    printf 'NACOS_ENCRYPTION_MASTER_KEY=%s\n' "$(random_base64 32)"
  } >"$target"
  chmod 600 "$target"
  printf 'Created restricted Nacos runtime env file: %s\n' "$target"
}

login_if_configured() {
  local username="${NACOS_USERNAME:-}"
  local password="${NACOS_PASSWORD:-}"
  local response
  if [[ -z "$username" && -z "$password" ]]; then
    return 0
  fi
  if [[ -z "$username" || -z "$password" ]]; then
    printf 'NACOS_USERNAME and NACOS_PASSWORD must be provided together.\n' >&2
    exit 1
  fi
  response="$(curl -fsS -X POST "${NACOS_ENDPOINT}/v1/auth/login" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}")"
  access_token="$(printf '%s' "$response" \
    | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [[ -z "$access_token" ]]; then
    printf 'Nacos login did not return an access token.\n' >&2
    exit 1
  fi
}

service_data_id() {
  printf '%s-%s.yaml' "$1" "$2"
}

encrypted_service_data_id() {
  printf 'cipher-acqaesgcm-%s-%s.yaml' "$1" "$2"
}

legacy_edge_data_id() {
  printf 'cipher-acqaesgcm-internal-auth-%s-%s.yaml' "$1" "$2"
}

# 返回单个服务需要的最小调用边集合；同一密钥仅复制到真实调用方和验签方的服务级配置中。
service_edges() {
  case "$1" in
    service-admin)
      printf '%s\n' merchant-admin admin-job admin-payment admin-clearing admin-settlement
      ;;
    service-checkout)
      printf '%s\n' gateway-checkout
      ;;
    service-clearing)
      printf '%s\n' admin-clearing job-clearing
      ;;
    service-data)
      printf '%s\n' job-data
      ;;
    service-gateway)
      printf '%s\n' gateway-checkout
      ;;
    service-job)
      printf '%s\n' admin-job job-payment job-clearing job-data
      ;;
    service-merchant)
      printf '%s\n' merchant-payment
      ;;
    service-openapi)
      printf '%s\n' openapi-payment openapi-payout gateway-checkout
      ;;
    service-payment)
      printf '%s\n' admin-payment job-payment merchant-payment openapi-payment payment-risk
      ;;
    service-payout)
      printf '%s\n' openapi-payout
      ;;
    service-risk)
      printf '%s\n' payment-risk
      ;;
    service-settlement)
      printf '%s\n' admin-settlement
      ;;
    *)
      printf 'Unsupported service: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

# 返回负责签名的调用方；merchant-admin 兼容边当前没有正常调用方，仅由 Admin 保留失败关闭凭据。
edge_caller() {
  case "$1" in
    merchant-admin) printf '%s' '' ;;
    admin-job|admin-payment|admin-clearing|admin-settlement) printf '%s' service-admin ;;
    job-payment|job-clearing|job-data) printf '%s' service-job ;;
    merchant-payment) printf '%s' service-merchant ;;
    openapi-payment|openapi-payout) printf '%s' service-openapi ;;
    payment-risk) printf '%s' service-payment ;;
    gateway-checkout) printf '%s' service-gateway ;;
    *)
      printf 'Unsupported internal-auth edge: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

# 返回验签方；gateway-checkout 同时保护 OpenAPI 和 Checkout 两个下游入口。
edge_receivers() {
  case "$1" in
    merchant-admin) printf '%s\n' service-admin ;;
    admin-job) printf '%s\n' service-job ;;
    admin-payment|job-payment|merchant-payment|openapi-payment) printf '%s\n' service-payment ;;
    admin-clearing|job-clearing) printf '%s\n' service-clearing ;;
    admin-settlement) printf '%s\n' service-settlement ;;
    job-data) printf '%s\n' service-data ;;
    openapi-payout) printf '%s\n' service-payout ;;
    payment-risk) printf '%s\n' service-risk ;;
    gateway-checkout) printf '%s\n' service-openapi service-checkout ;;
    *)
      printf 'Unsupported internal-auth edge: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

edge_services() {
  local caller
  caller="$(edge_caller "$1")"
  if [[ -n "$caller" ]]; then
    printf '%s\n' "$caller"
  fi
  edge_receivers "$1"
}

service_config_template() {
  printf '%s/%s-%s.yaml' "$SERVICE_CONFIG_TEMPLATE_DIR" "$1" "$2"
}

fetch_data_id() {
  local data_id="$1"
  local namespace="$2"
  local output="$3"
  local headers="$4"
  local http_status
  local args=(
    -sS -G -D "$headers" -o "$output" -w '%{http_code}'
    "${NACOS_ENDPOINT}/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${NACOS_GROUP}"
    --data-urlencode "tenant=${namespace}"
  )
  if [[ -n "$access_token" ]]; then
    args+=(--data-urlencode "accessToken=${access_token}")
  fi
  if ! http_status="$(curl "${args[@]}")"; then
    printf 'Nacos configuration query transport failed: %s.\n' "$data_id" >&2
    return 2
  fi
  case "$http_status" in
    200) return 0 ;;
    404) return 1 ;;
    *)
      printf 'Nacos configuration query failed for %s with HTTP %s.\n' "$data_id" "$http_status" >&2
      return 2
      ;;
  esac
}

publish_data_id() {
  local data_id="$1"
  local namespace="$2"
  local content_file="$3"
  local response_file="${work_dir}/publish-response"
  local http_status
  local args=(
    -sS -X POST -o "$response_file" -w '%{http_code}'
    "${NACOS_ENDPOINT}/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${NACOS_GROUP}"
    --data-urlencode "tenant=${namespace}"
    --data-urlencode "type=yaml"
    --data-urlencode "content@${content_file}"
  )
  if [[ -n "$access_token" ]]; then
    args+=(--data-urlencode "accessToken=${access_token}")
  fi
  if ! http_status="$(curl "${args[@]}")"; then
    printf 'Nacos configuration publish transport failed: %s.\n' "$data_id" >&2
    exit 1
  fi
  if [[ "$http_status" != "200" ]] || ! grep -qx 'true' "$response_file"; then
    printf 'Nacos configuration publish failed for %s with HTTP %s.\n' "$data_id" "$http_status" >&2
    exit 1
  fi
}

delete_data_id() {
  local data_id="$1"
  local namespace="$2"
  local response_file="${work_dir}/delete-response"
  local http_status
  local args=(
    -sS -X DELETE -o "$response_file" -w '%{http_code}'
    "${NACOS_ENDPOINT}/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${NACOS_GROUP}"
    --data-urlencode "tenant=${namespace}"
  )
  if [[ -n "$access_token" ]]; then
    args+=(--data-urlencode "accessToken=${access_token}")
  fi
  if ! http_status="$(curl "${args[@]}")"; then
    printf 'Nacos configuration delete transport failed: %s.\n' "$data_id" >&2
    exit 1
  fi
  if [[ "$http_status" != "200" ]] || ! grep -qx 'true' "$response_file"; then
    printf 'Nacos configuration delete failed for %s with HTTP %s.\n' "$data_id" "$http_status" >&2
    exit 1
  fi
}

encrypted_data_key_from() {
  awk 'tolower($1) == "encrypted-data-key:" {print $2}' "$1" | tr -d '\r' | head -n 1
}

normalize_empty_secret() {
  local value="$1"
  if [[ "$value" == '""' || "$value" == "''" ]]; then
    value=""
  fi
  printf '%s' "$value"
}

legacy_secret_from() {
  local field="$1"
  local file="$2"
  local value
  value="$(sed -n "s/^[[:space:]]*${field}:[[:space:]]*//p" "$file" | head -n 1)"
  normalize_empty_secret "$value"
}

verify_secret_value() {
  local value="$1"
  local description="$2"
  if [[ ${#value} -lt 32 || "$value" == *'${'* ]]; then
    printf 'Invalid or unresolved internal-auth secret: %s.\n' "$description" >&2
    exit 1
  fi
}

write_generated_edge_source() {
  local target="$1"
  local edge="$2"
  local active_secret="$3"
  write_edge_source "$target" "$edge" "$active_secret" ""
}

write_edge_source() {
  local target="$1"
  local edge="$2"
  local active_secret="$3"
  local previous_secret="$4"
  local previous_yaml='""'
  if [[ -n "$previous_secret" ]]; then
    previous_yaml="$previous_secret"
  fi
  {
    printf 'acquiring:\n'
    printf '  internal-auth:\n'
    printf '    edges:\n'
    printf '      %s:\n' "$edge"
    printf '        active-secret: %s\n' "$active_secret"
    printf '        previous-secret: %s\n' "$previous_yaml"
  } >"$target"
}

# 迁移旧 cipher 服务 DataId 时优先复用既有调用边密钥，禁止因配置整理而静默轮换密钥。
prepare_edge_source_from_service_bundles() {
  local edge="$1"
  local environment="$2"
  local namespace="$3"
  local output="$4"
  local service
  local data_id
  local source_file
  local header_file
  local fetch_status
  local active_secret
  local previous_secret
  while IFS= read -r service; do
    [[ -n "$service" ]] || continue
    for data_id in \
      "$(encrypted_service_data_id "$service" "$environment")" \
      "$(service_data_id "$service" "$environment")"; do
      source_file="${work_dir}/edge-source-${edge}-${service}.yaml"
      header_file="${work_dir}/edge-source-${edge}-${service}.headers"
      fetch_status=0
      fetch_data_id "$data_id" "$namespace" "$source_file" "$header_file" || fetch_status=$?
      if [[ $fetch_status -eq 1 ]]; then
        continue
      elif [[ $fetch_status -ne 0 ]]; then
        exit 1
      fi
      active_secret="$(bundle_edge_secret_from "$source_file" "$edge" active-secret)"
      previous_secret="$(bundle_edge_secret_from "$source_file" "$edge" previous-secret)"
      if [[ -z "$active_secret" ]]; then
        continue
      fi
      verify_secret_value "$active_secret" "service bundle source ${service}/${edge} active"
      if [[ -n "$previous_secret" ]]; then
        verify_secret_value "$previous_secret" "service bundle source ${service}/${edge} previous"
      fi
      write_edge_source "$output" "$edge" "$active_secret" "$previous_secret"
      return 0
    done
  done < <(edge_services "$edge")
  return 1
}

verify_legacy_edge_file() {
  local edge="$1"
  local content_file="$2"
  local header_file="$3"
  local active_secret
  local previous_secret
  local encrypted_data_key
  active_secret="$(legacy_secret_from active-secret "$content_file")"
  previous_secret="$(legacy_secret_from previous-secret "$content_file")"
  encrypted_data_key="$(encrypted_data_key_from "$header_file")"
  if ! grep -q "^[[:space:]]*${edge}:[[:space:]]*$" "$content_file" \
      || [[ "$encrypted_data_key" != v1.* ]]; then
    printf 'Legacy encrypted edge configuration validation failed: %s.\n' "$edge" >&2
    exit 1
  fi
  verify_secret_value "$active_secret" "legacy edge ${edge} active"
  if [[ -n "$previous_secret" ]]; then
    verify_secret_value "$previous_secret" "legacy edge ${edge} previous"
  fi
}

# 在迁移前一次性取得每条调用边的唯一密钥来源；新环境缺少旧边配置时只生成一次并复用到参与服务。
prepare_edge_sources() {
  local environment="$1"
  local namespace="$2"
  local edge
  local content_file
  local header_file
  local fetch_status
  local reused=0
  local reused_service_bundle=0
  local generated=0
  for edge in "${EDGES[@]}"; do
    content_file="${work_dir}/edge-${edge}.yaml"
    header_file="${work_dir}/edge-${edge}.headers"
    fetch_status=0
    fetch_data_id "$(legacy_edge_data_id "$edge" "$environment")" "$namespace" \
      "$content_file" "$header_file" || fetch_status=$?
    if [[ $fetch_status -eq 0 ]]; then
      verify_legacy_edge_file "$edge" "$content_file" "$header_file"
      reused=$((reused + 1))
    elif [[ $fetch_status -eq 1 ]]; then
      if prepare_edge_source_from_service_bundles \
          "$edge" "$environment" "$namespace" "$content_file"; then
        reused_service_bundle=$((reused_service_bundle + 1))
      else
        write_generated_edge_source "$content_file" "$edge" "$(random_base64 "$SECRET_BYTES")"
        generated=$((generated + 1))
      fi
    else
      exit 1
    fi
  done
  printf 'Internal-auth edge sources prepared: reused-edge=%d, reused-service=%d, generated=%d, environment=%s.\n' \
    "$reused" "$reused_service_bundle" "$generated" "$environment"
}

bundle_edge_secret_from() {
  local content_file="$1"
  local edge="$2"
  local field="$3"
  local layout
  local edge_indent
  local field_indent
  local value
  if ! layout="$(service_auth_bundle_format "$content_file")"; then
    printf '%s' ''
    return 0
  fi
  if [[ "$layout" == "nested" ]]; then
    edge_indent='      '
    field_indent='        '
  else
    edge_indent='  '
    field_indent='    '
  fi
  value="$(awk -v edge="$edge" -v field="$field" \
      -v edge_indent="$edge_indent" -v field_indent="$field_indent" '
    $0 == edge_indent edge ":" { in_edge=1; next }
    in_edge && index($0, edge_indent) == 1 \
        && substr($0, length(edge_indent) + 1, 1) != " " { exit }
    in_edge && index($0, field_indent field ":") == 1 {
      sub("^" field_indent field ":[[:space:]]*", "")
      print
      exit
    }
  ' "$content_file")"
  normalize_empty_secret "$value"
}

# 识别服务配置中的内部鉴权布局。迁移期间兼容旧点号根键，所有新输出统一使用标准嵌套 YAML。
service_auth_bundle_format() {
  local content_file="$1"
  local legacy_count
  local nested_count
  local acquiring_root_count
  legacy_count="$(grep -c '^acquiring\.internal-auth\.edges:[[:space:]]*$' "$content_file" || true)"
  acquiring_root_count="$(grep -c '^acquiring:[[:space:]]*$' "$content_file" || true)"
  nested_count="$(awk '
    BEGIN { in_acquiring=0; in_internal_auth=0; count=0 }
    $0 == "acquiring:" {
      in_acquiring=1
      in_internal_auth=0
      next
    }
    in_acquiring && /^[^[:space:]#]/ {
      in_acquiring=0
      in_internal_auth=0
    }
    in_acquiring && $0 == "  internal-auth:" {
      in_internal_auth=1
      next
    }
    in_internal_auth && /^  [^ ]/ {
      in_internal_auth=0
    }
    in_internal_auth && $0 == "    edges:" {
      count++
    }
    END { print count }
  ' "$content_file")"
  if [[ "$legacy_count" -eq 1 && "$nested_count" -eq 0 ]]; then
    printf 'legacy'
    return 0
  fi
  if [[ "$legacy_count" -eq 0 && "$nested_count" -eq 1 && "$acquiring_root_count" -eq 1 ]]; then
    printf 'nested'
    return 0
  fi
  return 1
}

edge_description() {
  case "$1" in
    merchant-admin) printf '遗留 Merchant 到 Admin 兼容接口验签，仅用于失败关闭保护。' ;;
    admin-job) printf 'Admin 调用 Job 管理命令时使用的签名与验签凭据。' ;;
    admin-payment) printf 'Admin 调用 Payment 状态变更和异常处置命令时使用的凭据。' ;;
    admin-clearing) printf 'Admin 调用 Clearing 人工清分执行与补偿命令时使用的凭据。' ;;
    admin-settlement) printf 'Admin 调用 Settlement 手工结算、审批、取消和冲正命令时使用的凭据。' ;;
    job-payment) printf 'Job 调用 Payment 到期处理与补偿命令时使用的凭据。' ;;
    job-clearing) printf 'Job 调用 Clearing 补偿命令时使用的凭据。' ;;
    job-data) printf 'Job 调用 Data 数据与商户通知补偿命令时使用的凭据。' ;;
    merchant-payment) printf 'Merchant 调用 Payment 退款、撤销、请款或预授权完成命令时使用的凭据。' ;;
    openapi-payment) printf 'OpenAPI 调用 Payment 支付受理与查询内部协议时使用的凭据。' ;;
    openapi-payout) printf 'OpenAPI 调用 Payout 代付受理与查询内部协议时使用的凭据。' ;;
    payment-risk) printf 'Payment 调用 Risk 实时风控评估时使用的凭据。' ;;
    gateway-checkout) printf 'Gateway 访问 OpenAPI 和 Checkout 托管收银台入口时使用的凭据。' ;;
    *) printf '内部服务调用边签名与验签凭据。' ;;
  esac
}

write_managed_auth_fragment_header() {
  local target="$1"
  {
    printf '  %s\n' "$AUTH_BUNDLE_MARKER"
    printf '  # 参数说明：内部服务 HMAC 凭据根节点；只允许由受控脚本维护，禁止人工复制到 common 配置。\n'
    printf '  internal-auth:\n'
    printf '    # 参数说明：按真实调用关系隔离的凭据集合；调用方与全部验签方必须保持同一调用边值一致。\n'
    printf '    edges:\n'
  } >"$target"
}

append_managed_auth_edge() {
  local target="$1"
  local edge="$2"
  local active_secret="$3"
  local previous_secret="$4"
  local previous_yaml='""'
  if [[ -n "$previous_secret" ]]; then
    previous_yaml="$previous_secret"
  fi
  {
    printf '      # 调用边说明：%s\n' "$(edge_description "$edge")"
    printf '      %s:\n' "$edge"
    printf '        # 参数说明：当前生效的 HMAC-SHA256 密钥，长度不得少于 32 字符；禁止写入日志、仓库或工单。\n'
    printf '        active-secret: %s\n' "$active_secret"
    printf '        # 参数说明：滚动轮换期间临时接受的上一版密钥；非轮换窗口必须为空字符串。\n'
    printf '        previous-secret: %s\n' "$previous_yaml"
  } >>"$target"
}

write_managed_auth_fragment_from_edge_sources() {
  local service="$1"
  local target="$2"
  local edge
  local active_secret
  local previous_secret
  write_managed_auth_fragment_header "$target"
  while IFS= read -r edge; do
    [[ -n "$edge" ]] || continue
    active_secret="$(legacy_secret_from active-secret "${work_dir}/edge-${edge}.yaml")"
    previous_secret="$(legacy_secret_from previous-secret "${work_dir}/edge-${edge}.yaml")"
    verify_secret_value "$active_secret" "edge source ${edge} active"
    append_managed_auth_edge "$target" "$edge" "$active_secret" "$previous_secret"
  done < <(service_edges "$service")
}

write_managed_auth_fragment_from_bundle() {
  local service="$1"
  local source_file="$2"
  local target="$3"
  local edge
  local active_secret
  local previous_secret
  write_managed_auth_fragment_header "$target"
  while IFS= read -r edge; do
    [[ -n "$edge" ]] || continue
    active_secret="$(bundle_edge_secret_from "$source_file" "$edge" active-secret)"
    previous_secret="$(bundle_edge_secret_from "$source_file" "$edge" previous-secret)"
    verify_secret_value "$active_secret" "service bundle source ${service}/${edge} active"
    append_managed_auth_edge "$target" "$edge" "$active_secret" "$previous_secret"
  done < <(service_edges "$service")
}

# 保留模板原有顺序和注释，把受控凭据片段插入唯一的 acquiring 根节点；无根节点时才追加新节点。
inject_managed_auth_fragment() {
  local base_file="$1"
  local fragment_file="$2"
  local output_file="$3"
  local acquiring_root_count
  local internal_auth_count
  acquiring_root_count="$(grep -c '^acquiring:[[:space:]]*$' "$base_file" || true)"
  if [[ "$acquiring_root_count" -gt 1 ]]; then
    printf 'Service configuration contains duplicate acquiring roots.\n' >&2
    exit 1
  fi
  internal_auth_count="$(grep -c '^  internal-auth:[[:space:]]*$' "$base_file" || true)"
  if [[ "$internal_auth_count" -gt 0 ]]; then
    printf 'Service configuration already contains an unmanaged internal-auth node.\n' >&2
    exit 1
  fi
  if ! awk -v fragment_file="$fragment_file" '
    function emit_fragment(line) {
      while ((getline line < fragment_file) > 0) {
        print line
      }
      close(fragment_file)
      inserted=1
    }
    BEGIN { in_acquiring=0; seen_acquiring=0; inserted=0 }
    $0 == "acquiring:" && !seen_acquiring {
      in_acquiring=1
      seen_acquiring=1
      print
      next
    }
    in_acquiring && /^[^[:space:]#]/ {
      emit_fragment()
      in_acquiring=0
    }
    { print }
    END {
      if (in_acquiring) {
        emit_fragment()
      } else if (!seen_acquiring) {
        if (NR > 0) print ""
        print "acquiring:"
        emit_fragment()
      }
      if (!inserted) exit 42
    }
  ' "$base_file" >"$output_file"; then
    printf 'Cannot merge managed internal-auth fragment into service configuration.\n' >&2
    exit 1
  fi
}

# 仅移除旧点号形式的受控凭据段，保留同一 DataId 中的业务配置与人工注释，供迁移为嵌套结构。
strip_legacy_auth_bundle() {
  local source_file="$1"
  local output_file="$2"
  if ! awk -v marker="$AUTH_BUNDLE_MARKER" '
    BEGIN { in_legacy=0; pending_marker=""; found_bundle=0 }
    $0 == marker {
      pending_marker=$0
      next
    }
    $0 ~ /^acquiring\.internal-auth\.edges:[[:space:]]*$/ {
      pending_marker=""
      in_legacy=1
      found_bundle=1
      next
    }
    in_legacy && /^[^[:space:]#]/ {
      in_legacy=0
    }
    in_legacy && /^#[^[:space:]]/ {
      in_legacy=0
    }
    !in_legacy {
      if (pending_marker != "") {
        print pending_marker
        pending_marker=""
      }
      print
    }
    END {
      if (!found_bundle) exit 42
      if (pending_marker != "") print pending_marker
    }
  ' "$source_file" >"$output_file"; then
    printf 'Cannot remove legacy internal-auth bundle from service configuration.\n' >&2
    exit 1
  fi
}

# 所有会被发布的服务配置统一转换为标准嵌套结构；旧点号结构只允许作为迁移输入读取。
normalize_service_bundle_to_nested() {
  local service="$1"
  local source_file="$2"
  local output_file="$3"
  local layout
  local base_file="${work_dir}/normalize-base-${service}-$$.yaml"
  local auth_file="${work_dir}/normalize-auth-${service}-$$.yaml"
  if ! layout="$(service_auth_bundle_format "$source_file")"; then
    printf 'Cannot normalize malformed internal-auth bundle: %s.\n' "$service" >&2
    exit 1
  fi
  if [[ "$layout" == "nested" ]]; then
    cp "$source_file" "$output_file"
    return
  fi
  write_managed_auth_fragment_from_bundle "$service" "$source_file" "$auth_file"
  strip_legacy_auth_bundle "$source_file" "$base_file"
  inject_managed_auth_fragment "$base_file" "$auth_file" "$output_file"
}

# 只提取受控 edges 映射的直接子 key，用于校验服务没有获得职责之外的内部调用凭据。
bundle_edges_from() {
  local content_file="$1"
  local layout
  if ! layout="$(service_auth_bundle_format "$content_file")"; then
    return 1
  fi
  if [[ "$layout" == "nested" ]]; then
    awk '
      $0 == "acquiring:" { in_acquiring=1; next }
      in_acquiring && /^[^[:space:]#]/ { exit }
      in_acquiring && $0 == "  internal-auth:" { in_internal_auth=1; next }
      in_internal_auth && /^  [^ ]/ { exit }
      in_internal_auth && $0 == "    edges:" { in_edges=1; next }
      in_edges && /^    [^ ]/ { exit }
      in_edges && /^      [a-z0-9-]+:[[:space:]]*$/ {
        value=$0
        sub(/^      /, "", value)
        sub(/:[[:space:]]*$/, "", value)
        print value
      }
    ' "$content_file"
  else
    awk '
      $0 ~ /^acquiring\.internal-auth\.edges:[[:space:]]*$/ { in_edges=1; next }
      in_edges && /^[^[:space:]#]/ { exit }
      in_edges && /^  [a-z0-9-]+:[[:space:]]*$/ {
        value=$0
        sub(/^  /, "", value)
        sub(/:[[:space:]]*$/, "", value)
        print value
      }
    ' "$content_file"
  fi
}

verify_service_bundle_file() {
  local service="$1"
  local content_file="$2"
  local header_file="$3"
  local edge
  local layout
  local marker_count
  local active_secret
  local previous_secret
  local edge_count
  local expected_edges_file="${work_dir}/expected-edges-${service}-$$.txt"
  local actual_edges_file="${work_dir}/actual-edges-${service}-$$.txt"
  : "$header_file"
  if ! layout="$(service_auth_bundle_format "$content_file")"; then
    printf 'Service configuration validation failed: %s.\n' "$service" >&2
    exit 1
  fi
  marker_count="$(sed 's/^[[:space:]]*//' "$content_file" | grep -Fxc "$AUTH_BUNDLE_MARKER" || true)"
  if [[ "$marker_count" -ne 1 ]]; then
    printf 'Managed internal-auth marker validation failed: %s.\n' "$service" >&2
    exit 1
  fi
  service_edges "$service" | sort -u >"$expected_edges_file"
  bundle_edges_from "$content_file" | sort >"$actual_edges_file"
  if ! cmp -s "$expected_edges_file" "$actual_edges_file"; then
    printf 'Internal-auth least-privilege edge validation failed: %s.\n' "$service" >&2
    exit 1
  fi
  while IFS= read -r edge; do
    [[ -n "$edge" ]] || continue
    if [[ "$layout" == "nested" ]]; then
      edge_count="$(grep -c "^      ${edge}:[[:space:]]*$" "$content_file" || true)"
    else
      edge_count="$(grep -c "^  ${edge}:[[:space:]]*$" "$content_file" || true)"
    fi
    if [[ "$edge_count" -ne 1 ]]; then
      printf 'Internal-auth edge occurrence validation failed: %s/%s.\n' "$service" "$edge" >&2
      exit 1
    fi
    active_secret="$(bundle_edge_secret_from "$content_file" "$edge" active-secret)"
    previous_secret="$(bundle_edge_secret_from "$content_file" "$edge" previous-secret)"
    verify_secret_value "$active_secret" "${service}/${edge} active"
    if [[ -n "$previous_secret" ]]; then
      verify_secret_value "$previous_secret" "${service}/${edge} previous"
      if [[ "$active_secret" == "$previous_secret" ]]; then
        printf 'Active and previous internal-auth secrets must differ: %s/%s.\n' "$service" "$edge" >&2
        exit 1
      fi
    fi
  done < <(service_edges "$service")
}

build_service_bundle() {
  local service="$1"
  local environment="$2"
  local namespace="$3"
  local output="$4"
  local base_file="${work_dir}/base-${service}.yaml"
  local base_headers="${work_dir}/base-${service}.headers"
  local fetch_status=0
  local encrypted_fetch_status=0
  local auth_file="${work_dir}/new-auth-${service}.yaml"
  local layout
  fetch_data_id "$(service_data_id "$service" "$environment")" "$namespace" \
    "$base_file" "$base_headers" || fetch_status=$?
  if [[ $fetch_status -eq 1 ]]; then
    encrypted_fetch_status=0
    fetch_data_id "$(encrypted_service_data_id "$service" "$environment")" "$namespace" \
      "$base_file" "$base_headers" || encrypted_fetch_status=$?
    if [[ $encrypted_fetch_status -eq 1 ]]; then
      : >"$base_file"
    elif [[ $encrypted_fetch_status -ne 0 ]]; then
      exit 1
    fi
  elif [[ $fetch_status -ne 0 ]]; then
    exit 1
  fi
  if layout="$(service_auth_bundle_format "$base_file")"; then
    if [[ "$layout" == "nested" ]]; then
      cp "$base_file" "$output"
    else
      normalize_service_bundle_to_nested "$service" "$base_file" "$output"
    fi
    return
  fi
  write_managed_auth_fragment_from_edge_sources "$service" "$auth_file"
  inject_managed_auth_fragment "$base_file" "$auth_file" "$output"
}

fetch_expected_bundle() {
  local service="$1"
  local environment="$2"
  local namespace="$3"
  local expected_file="$4"
  local fetched_file="${work_dir}/expected-${service}.yaml"
  local headers_file="${work_dir}/expected-${service}.headers"
  local attempt
  local fetch_status
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    fetch_status=0
    fetch_data_id "$(service_data_id "$service" "$environment")" "$namespace" \
      "$fetched_file" "$headers_file" || fetch_status=$?
    if [[ $fetch_status -eq 0 ]] && cmp -s "$expected_file" "$fetched_file"; then
      verify_service_bundle_file "$service" "$fetched_file" "$headers_file"
      return 0
    elif [[ $fetch_status -ne 0 && $fetch_status -ne 1 ]]; then
      return "$fetch_status"
    fi
    sleep 1
  done
  printf 'Published service configuration did not converge: %s.\n' "$service" >&2
  return 1
}

migrate_service_bundles() {
  local environment="$1"
  local namespace="$2"
  local service
  local target_file
  local fetched_file
  local headers_file
  local fetch_status
  local migrated=0
  local existing=0
  local layout
  prepare_edge_sources "$environment" "$namespace"
  for service in "${SERVICES[@]}"; do
    fetched_file="${work_dir}/existing-${service}.yaml"
    headers_file="${work_dir}/existing-${service}.headers"
    fetch_status=0
    fetch_data_id "$(service_data_id "$service" "$environment")" "$namespace" \
      "$fetched_file" "$headers_file" || fetch_status=$?
    if [[ $fetch_status -eq 0 ]]; then
      if layout="$(service_auth_bundle_format "$fetched_file")" \
          && [[ "$layout" == "nested" ]]; then
        verify_service_bundle_file "$service" "$fetched_file" "$headers_file"
        existing=$((existing + 1))
        continue
      fi
    elif [[ $fetch_status -ne 1 ]]; then
      exit 1
    fi
    target_file="${work_dir}/new-${service}.yaml"
    build_service_bundle "$service" "$environment" "$namespace" "$target_file"
    publish_data_id "$(service_data_id "$service" "$environment")" "$namespace" "$target_file"
    fetch_expected_bundle "$service" "$environment" "$namespace" "$target_file"
    migrated=$((migrated + 1))
  done
  verify_all_service_bundles "$environment" "$namespace"
  printf 'Service DataIds verified: migrated=%d, existing=%d, environment=%s.\n' \
    "$migrated" "$existing" "$environment"
}

load_service_bundle() {
  local service="$1"
  local environment="$2"
  local namespace="$3"
  local content_file="${work_dir}/bundle-${service}.yaml"
  local header_file="${work_dir}/bundle-${service}.headers"
  local fetch_status=0
  fetch_data_id "$(service_data_id "$service" "$environment")" "$namespace" \
    "$content_file" "$header_file" || fetch_status=$?
  if [[ $fetch_status -eq 1 ]]; then
    printf 'Required service DataId is missing: %s.\n' \
      "$(service_data_id "$service" "$environment")" >&2
    exit 1
  elif [[ $fetch_status -ne 0 ]]; then
    exit 1
  fi
  verify_service_bundle_file "$service" "$content_file" "$header_file"
}

load_all_service_bundles() {
  local environment="$1"
  local namespace="$2"
  local service
  for service in "${SERVICES[@]}"; do
    load_service_bundle "$service" "$environment" "$namespace"
  done
}

verify_edge_consistency() {
  local edge="$1"
  local service
  local active_secret
  local previous_secret
  local expected_active=""
  local expected_previous=""
  local initialized=false
  while IFS= read -r service; do
    [[ -n "$service" ]] || continue
    active_secret="$(bundle_edge_secret_from "${work_dir}/bundle-${service}.yaml" "$edge" active-secret)"
    previous_secret="$(bundle_edge_secret_from "${work_dir}/bundle-${service}.yaml" "$edge" previous-secret)"
    if [[ "$initialized" == false ]]; then
      expected_active="$active_secret"
      expected_previous="$previous_secret"
      initialized=true
    elif [[ "$active_secret" != "$expected_active" || "$previous_secret" != "$expected_previous" ]]; then
      printf 'Internal-auth edge values are inconsistent across service bundles: %s.\n' "$edge" >&2
      exit 1
    fi
  done < <(edge_services "$edge")
}

verify_loaded_edge_consistency() {
  local edge
  for edge in "${EDGES[@]}"; do
    verify_edge_consistency "$edge"
  done
}

verify_all_service_bundles() {
  local environment="$1"
  local namespace="$2"
  load_all_service_bundles "$environment" "$namespace"
  verify_loaded_edge_consistency
  printf 'Service configuration set verified: services=%d, edges=%d, environment=%s.\n' \
    "${#SERVICES[@]}" "${#EDGES[@]}" "$environment"
}

# 服务模板只保存非敏感业务配置和对内部调用边的属性引用，真实密钥必须来自现有 Nacos 服务配置。
validate_service_config_template() {
  local service="$1"
  local environment="$2"
  local template_file
  template_file="$(service_config_template "$service" "$environment")"
  if [[ ! -s "$template_file" ]]; then
    printf 'Required service configuration template is missing or empty: %s.\n' "$template_file" >&2
    exit 1
  fi
  if service_auth_bundle_format "$template_file" >/dev/null 2>&1 \
      || grep -Fq "$AUTH_BUNDLE_MARKER" "$template_file" \
      || grep -q '^acquiring\.internal-auth\.edges:[[:space:]]*$' "$template_file" \
      || grep -q 'cipher-acqaesgcm-' "$template_file"; then
    printf 'Service configuration template must not contain managed secret material: %s.\n' \
      "$template_file" >&2
    exit 1
  fi
}

build_synced_service_config() {
  local service="$1"
  local environment="$2"
  local current_file="${work_dir}/bundle-${service}.yaml"
  local template_file
  local auth_file="${work_dir}/auth-${service}.yaml"
  local expected_file="${work_dir}/synced-${service}.yaml"
  local validation_headers="${work_dir}/synced-${service}.headers"
  template_file="$(service_config_template "$service" "$environment")"
  validate_service_config_template "$service" "$environment"
  write_managed_auth_fragment_from_bundle "$service" "$current_file" "$auth_file"
  inject_managed_auth_fragment "$template_file" "$auth_file" "$expected_file"
  : >"$validation_headers"
  verify_service_bundle_file "$service" "$expected_file" "$validation_headers"
}

prepare_synced_service_configs() {
  local environment="$1"
  local namespace="$2"
  local service
  load_all_service_bundles "$environment" "$namespace"
  verify_loaded_edge_consistency
  for service in "${SERVICES[@]}"; do
    build_synced_service_config "$service" "$environment"
  done
}

plan_service_configs() {
  local environment="$1"
  local namespace="$2"
  local service
  local changed=0
  local unchanged=0
  prepare_synced_service_configs "$environment" "$namespace"
  for service in "${SERVICES[@]}"; do
    if cmp -s "${work_dir}/bundle-${service}.yaml" "${work_dir}/synced-${service}.yaml"; then
      printf 'Service configuration unchanged: %s-%s.yaml.\n' "$service" "$environment"
      unchanged=$((unchanged + 1))
    else
      printf 'Service configuration update planned: %s-%s.yaml.\n' "$service" "$environment"
      changed=$((changed + 1))
    fi
  done
  printf 'Service configuration plan verified: changed=%d, unchanged=%d, environment=%s.\n' \
    "$changed" "$unchanged" "$environment"
}

backup_service_configs() {
  local environment="$1"
  local namespace="$2"
  local backup_dir="$3"
  local service
  umask 077
  mkdir -p "$backup_dir"
  chmod 700 "$backup_dir"
  for service in "${SERVICES[@]}"; do
    cp "${work_dir}/bundle-${service}.yaml" \
      "${backup_dir}/$(service_data_id "$service" "$environment")"
  done
  {
    printf 'environment=%s\n' "$environment"
    printf 'namespace=%s\n' "$namespace"
    printf 'group=%s\n' "$NACOS_GROUP"
    printf 'services=%d\n' "${#SERVICES[@]}"
  } >"${backup_dir}/manifest.txt"
  chmod 600 "$backup_dir"/*
}

# 为每次发布创建不可复用的受限备份目录；同一秒内并发执行也不会覆盖已有密钥备份。
create_service_config_backup_dir() {
  local environment="$1"
  local timestamp
  umask 077
  mkdir -p "$SERVICE_CONFIG_BACKUP_ROOT"
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  mktemp -d "${SERVICE_CONFIG_BACKUP_ROOT}/${environment}-${timestamp}-XXXXXX"
}

sync_service_configs() {
  local environment="$1"
  local namespace="$2"
  local service
  local backup_dir
  local changed=0
  local unchanged=0
  if [[ "${CONFIRM_PUBLISH_SERVICE_CONFIGS:-}" != "YES" ]]; then
    printf 'Set CONFIRM_PUBLISH_SERVICE_CONFIGS=YES after reviewing plan-service-configs output.\n' >&2
    exit 1
  fi
  prepare_synced_service_configs "$environment" "$namespace"
  backup_dir="$(create_service_config_backup_dir "$environment")"
  backup_service_configs "$environment" "$namespace" "$backup_dir"
  for service in "${SERVICES[@]}"; do
    if cmp -s "${work_dir}/bundle-${service}.yaml" "${work_dir}/synced-${service}.yaml"; then
      unchanged=$((unchanged + 1))
      continue
    fi
    publish_data_id "$(service_data_id "$service" "$environment")" "$namespace" \
      "${work_dir}/synced-${service}.yaml"
    fetch_expected_bundle "$service" "$environment" "$namespace" \
      "${work_dir}/synced-${service}.yaml"
    changed=$((changed + 1))
  done
  verify_all_service_bundles "$environment" "$namespace"
  printf 'Service configurations synchronized: changed=%d, unchanged=%d, environment=%s, backup=%s.\n' \
    "$changed" "$unchanged" "$environment" "$backup_dir"
}

validate_service_config_backup() {
  local environment="$1"
  local namespace="$2"
  local backup_dir="$3"
  local manifest="${backup_dir}/manifest.txt"
  local service
  if [[ "$backup_dir" != /* || ! -d "$backup_dir" || -L "$backup_dir" ]]; then
    printf 'Backup directory must be an existing absolute non-symlink directory.\n' >&2
    exit 1
  fi
  if [[ ! -f "$manifest" || -L "$manifest" ]]; then
    printf 'Backup manifest is missing or unsafe: %s.\n' "$manifest" >&2
    exit 1
  fi
  grep -Fxq "environment=${environment}" "$manifest" \
    && grep -Fxq "namespace=${namespace}" "$manifest" \
    && grep -Fxq "group=${NACOS_GROUP}" "$manifest" \
    && grep -Fxq "services=${#SERVICES[@]}" "$manifest" || {
      printf 'Backup manifest does not match the requested Nacos target.\n' >&2
      exit 1
    }
  for service in "${SERVICES[@]}"; do
    local backup_file="${backup_dir}/$(service_data_id "$service" "$environment")"
    if [[ ! -s "$backup_file" || -L "$backup_file" ]]; then
      printf 'Backup service configuration is missing, empty, or unsafe: %s.\n' \
        "$backup_file" >&2
      exit 1
    fi
    verify_service_bundle_file "$service" "$backup_file" "${work_dir}/restore-validation.headers"
    normalize_service_bundle_to_nested \
      "$service" "$backup_file" "${work_dir}/bundle-${service}.yaml"
    verify_service_bundle_file "$service" "${work_dir}/bundle-${service}.yaml" \
      "${work_dir}/restore-validation.headers"
  done
  verify_loaded_edge_consistency
}

# 同批恢复全部服务配置，禁止选择性恢复调用边参与方，避免调用方与验签方密钥不一致。
restore_service_configs() {
  local environment="$1"
  local namespace="$2"
  local backup_dir="$3"
  local service
  if [[ "${CONFIRM_RESTORE_SERVICE_CONFIGS:-}" != "YES" ]]; then
    printf 'Set CONFIRM_RESTORE_SERVICE_CONFIGS=YES only after validating the backup target.\n' >&2
    exit 1
  fi
  validate_service_config_backup "$environment" "$namespace" "$backup_dir"
  for service in "${SERVICES[@]}"; do
    local restored_file="${work_dir}/bundle-${service}.yaml"
    publish_data_id "$(service_data_id "$service" "$environment")" "$namespace" "$restored_file"
    fetch_expected_bundle "$service" "$environment" "$namespace" "$restored_file"
  done
  verify_all_service_bundles "$environment" "$namespace"
  printf 'Service configurations restored and verified: services=%d, environment=%s, backup=%s.\n' \
    "${#SERVICES[@]}" "$environment" "$backup_dir"
}

replace_bundle_edge() {
  local content_file="$1"
  local edge="$2"
  local active_secret="$3"
  local previous_secret="$4"
  local output="${work_dir}/replace-${edge}-$$.yaml"
  local layout
  local edge_indent
  local field_indent
  local previous_yaml='""'
  if [[ -n "$previous_secret" ]]; then
    previous_yaml="$previous_secret"
  fi
  if ! layout="$(service_auth_bundle_format "$content_file")"; then
    printf 'Cannot identify internal-auth layout while updating edge: %s.\n' "$edge" >&2
    exit 1
  fi
  if [[ "$layout" == "nested" ]]; then
    edge_indent='      '
    field_indent='        '
  else
    edge_indent='  '
    field_indent='    '
  fi
  if ! awk -v edge="$edge" -v active="$active_secret" -v previous="$previous_yaml" \
      -v edge_indent="$edge_indent" -v field_indent="$field_indent" '
    BEGIN { in_edge=0; found_edge=0; found_active=0; found_previous=0 }
    $0 == edge_indent edge ":" { in_edge=1; found_edge=1; print; next }
    in_edge && index($0, edge_indent) == 1 \
        && substr($0, length(edge_indent) + 1, 1) != " " { in_edge=0 }
    in_edge && index($0, field_indent "active-secret:") == 1 {
      print field_indent "active-secret: " active
      found_active=1
      next
    }
    in_edge && index($0, field_indent "previous-secret:") == 1 {
      print field_indent "previous-secret: " previous
      found_previous=1
      next
    }
    { print }
    END {
      if (!found_edge || !found_active || !found_previous) exit 42
    }
  ' "$content_file" >"$output"; then
    printf 'Cannot update missing or malformed edge in service bundle: %s.\n' "$edge" >&2
    exit 1
  fi
  mv "$output" "$content_file"
}

publish_updated_service_bundle() {
  local service="$1"
  local environment="$2"
  local namespace="$3"
  local content_file="${work_dir}/bundle-${service}.yaml"
  local normalized_file="${work_dir}/normalized-${service}.yaml"
  normalize_service_bundle_to_nested "$service" "$content_file" "$normalized_file"
  mv "$normalized_file" "$content_file"
  verify_service_bundle_file "$service" "$content_file" "${work_dir}/publish-validation.headers"
  publish_data_id "$(service_data_id "$service" "$environment")" "$namespace" "$content_file"
  fetch_expected_bundle "$service" "$environment" "$namespace" "$content_file"
}

rotate_prepare() {
  local environment="$1"
  local namespace="$2"
  local edge="$3"
  local reference_service
  local old_active
  local old_previous
  local new_active
  local receiver
  load_all_service_bundles "$environment" "$namespace"
  verify_edge_consistency "$edge"
  reference_service="$(edge_services "$edge" | head -n 1)"
  old_active="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_service}.yaml" "$edge" active-secret)"
  old_previous="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_service}.yaml" "$edge" previous-secret)"
  if [[ -n "$old_previous" ]]; then
    printf 'Edge already has a previous-secret; finish or retire the current rotation first: %s.\n' "$edge" >&2
    exit 1
  fi
  new_active="$(random_base64 "$SECRET_BYTES")"
  while IFS= read -r receiver; do
    [[ -n "$receiver" ]] || continue
    replace_bundle_edge "${work_dir}/bundle-${receiver}.yaml" "$edge" "$new_active" "$old_active"
    publish_updated_service_bundle "$receiver" "$environment" "$namespace"
  done < <(edge_receivers "$edge")
  printf 'Rotation prepared on verifier service bundles: %s (%s). Restart or refresh verifiers before activate.\n' \
    "$edge" "$environment"
}

rotate_activate() {
  local environment="$1"
  local namespace="$2"
  local edge="$3"
  local caller
  local reference_receiver
  local new_active
  local old_active
  local receiver
  local receiver_active
  local receiver_previous
  local caller_active
  local caller_previous
  load_all_service_bundles "$environment" "$namespace"
  caller="$(edge_caller "$edge")"
  if [[ -z "$caller" ]]; then
    printf 'Edge has no active caller bundle; verifier preparation is the complete activation step: %s.\n' "$edge"
    return
  fi
  reference_receiver="$(edge_receivers "$edge" | head -n 1)"
  new_active="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_receiver}.yaml" "$edge" active-secret)"
  old_active="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_receiver}.yaml" "$edge" previous-secret)"
  if [[ -z "$old_active" ]]; then
    printf 'Verifier bundles are not prepared for rotation: %s.\n' "$edge" >&2
    exit 1
  fi
  while IFS= read -r receiver; do
    [[ -n "$receiver" ]] || continue
    receiver_active="$(bundle_edge_secret_from "${work_dir}/bundle-${receiver}.yaml" "$edge" active-secret)"
    receiver_previous="$(bundle_edge_secret_from "${work_dir}/bundle-${receiver}.yaml" "$edge" previous-secret)"
    if [[ "$receiver_active" != "$new_active" || "$receiver_previous" != "$old_active" ]]; then
      printf 'Verifier bundles are inconsistent during rotation: %s.\n' "$edge" >&2
      exit 1
    fi
  done < <(edge_receivers "$edge")
  caller_active="$(bundle_edge_secret_from "${work_dir}/bundle-${caller}.yaml" "$edge" active-secret)"
  caller_previous="$(bundle_edge_secret_from "${work_dir}/bundle-${caller}.yaml" "$edge" previous-secret)"
  if [[ "$caller_active" == "$new_active" && "$caller_previous" == "$old_active" ]]; then
    printf 'Caller bundle is already activated for edge: %s.\n' "$edge"
    return
  fi
  if [[ "$caller_active" != "$old_active" || -n "$caller_previous" ]]; then
    printf 'Caller bundle does not match the prepared verifier compatibility window: %s.\n' "$edge" >&2
    exit 1
  fi
  replace_bundle_edge "${work_dir}/bundle-${caller}.yaml" "$edge" "$new_active" "$old_active"
  publish_updated_service_bundle "$caller" "$environment" "$namespace"
  verify_edge_consistency "$edge"
  printf 'Rotation activated on caller service bundle: %s (%s).\n' "$edge" "$environment"
}

retire_previous() {
  local environment="$1"
  local namespace="$2"
  local edge="$3"
  local reference_service
  local active_secret
  local previous_secret
  local service
  load_all_service_bundles "$environment" "$namespace"
  verify_edge_consistency "$edge"
  reference_service="$(edge_services "$edge" | head -n 1)"
  active_secret="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_service}.yaml" "$edge" active-secret)"
  previous_secret="$(bundle_edge_secret_from "${work_dir}/bundle-${reference_service}.yaml" "$edge" previous-secret)"
  if [[ -z "$previous_secret" ]]; then
    printf 'Edge has no previous-secret to retire: %s.\n' "$edge" >&2
    exit 1
  fi
  while IFS= read -r service; do
    [[ -n "$service" ]] || continue
    replace_bundle_edge "${work_dir}/bundle-${service}.yaml" "$edge" "$active_secret" ""
    publish_updated_service_bundle "$service" "$environment" "$namespace"
  done < <(edge_services "$edge")
  verify_edge_consistency "$edge"
  printf 'Retired previous secret from all participating service bundles: %s (%s).\n' \
    "$edge" "$environment"
}

cleanup_legacy_configs() {
  local environment="$1"
  local namespace="$2"
  local edge
  local service
  local data_id
  local fetch_status
  local deleted=0
  local absent=0
  if [[ "${CONFIRM_DELETE_LEGACY_CONFIGS:-}" != "YES" ]]; then
    printf 'Set CONFIRM_DELETE_LEGACY_CONFIGS=YES only after ordinary service DataIds pass verification.\n' >&2
    exit 1
  fi
  verify_all_service_bundles "$environment" "$namespace"
  for edge in "${EDGES[@]}"; do
    data_id="$(legacy_edge_data_id "$edge" "$environment")"
    fetch_status=0
    fetch_data_id "$data_id" "$namespace" "${work_dir}/cleanup-content" \
      "${work_dir}/cleanup-headers" || fetch_status=$?
    if [[ $fetch_status -eq 0 ]]; then
      delete_data_id "$data_id" "$namespace"
      deleted=$((deleted + 1))
    elif [[ $fetch_status -eq 1 ]]; then
      absent=$((absent + 1))
    else
      exit 1
    fi
  done
  for service in "${SERVICES[@]}"; do
    data_id="$(encrypted_service_data_id "$service" "$environment")"
    fetch_status=0
    fetch_data_id "$data_id" "$namespace" "${work_dir}/cleanup-content" \
      "${work_dir}/cleanup-headers" || fetch_status=$?
    if [[ $fetch_status -eq 0 ]]; then
      delete_data_id "$data_id" "$namespace"
      deleted=$((deleted + 1))
    elif [[ $fetch_status -eq 1 ]]; then
      absent=$((absent + 1))
    else
      exit 1
    fi
  done
  printf 'Legacy Nacos DataIds removed: deleted=%d, already-absent=%d, environment=%s.\n' \
    "$deleted" "$absent" "$environment"
}

main() {
  local command="${1:-}"
  require_command curl
  require_command openssl
  require_command cmp
  case "$command" in
    init-container-env)
      [[ $# -eq 2 ]] || { usage >&2; exit 1; }
      initialize_container_env "$2"
      return
      ;;
    migrate|verify|plan-service-configs|sync-service-configs|cleanup-legacy)
      [[ $# -eq 3 ]] || { usage >&2; exit 1; }
      require_environment "$2"
      require_namespace "$3"
      ;;
    restore-service-configs)
      [[ $# -eq 4 ]] || { usage >&2; exit 1; }
      require_environment "$2"
      require_namespace "$3"
      ;;
    rotate-prepare|rotate-activate|retire-previous)
      [[ $# -eq 4 ]] || { usage >&2; exit 1; }
      require_environment "$2"
      require_namespace "$3"
      require_edge "$4"
      ;;
    *)
      usage >&2
      exit 1
      ;;
  esac

  work_dir="$(mktemp -d /tmp/acquiring-nacos-service-config.XXXXXX)"
  trap 'rm -rf "$work_dir"' EXIT
  login_if_configured

  case "$command" in
    migrate) migrate_service_bundles "$2" "$3" ;;
    verify) verify_all_service_bundles "$2" "$3" ;;
    plan-service-configs) plan_service_configs "$2" "$3" ;;
    sync-service-configs) sync_service_configs "$2" "$3" ;;
    restore-service-configs) restore_service_configs "$2" "$3" "$4" ;;
    rotate-prepare) rotate_prepare "$2" "$3" "$4" ;;
    rotate-activate) rotate_activate "$2" "$3" "$4" ;;
    retire-previous) retire_previous "$2" "$3" "$4" ;;
    cleanup-legacy) cleanup_legacy_configs "$2" "$3" ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi

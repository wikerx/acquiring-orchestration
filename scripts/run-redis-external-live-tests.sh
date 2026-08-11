#!/usr/bin/env bash

set -euo pipefail

# 外部 Live 测试会访问 MPGS Sandbox、运行中的 OpenAPI 服务或真实 MySQL，必须显式确认。
if [[ "${REDIS_EXTERNAL_TEST_ACKNOWLEDGED:-false}" != "true" ]]; then
  echo "Refusing external tests: set REDIS_EXTERNAL_TEST_ACKNOWLEDGED=true after confirming sandbox/UAT scope" >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
live_suite="${REDIS_EXTERNAL_LIVE_SUITE:-all}"
evidence_root="${REDIS_EXTERNAL_EVIDENCE_DIR:-${project_root}/target/redis-readiness/live}"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_dir="${evidence_root}/${run_id}"

mkdir -p "${evidence_dir}"

require_value() {
  variable_name="$1"
  eval "variable_value=\${${variable_name}:-}"
  if [[ -z "${variable_value}" ]]; then
    echo "Missing required environment variable: ${variable_name}" >&2
    exit 2
  fi
}

run_maven_with_evidence() {
  evidence_name="$1"
  shift
  (
    cd "${project_root}"
    JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" mvn "$@"
  ) 2>&1 | tee "${evidence_dir}/${evidence_name}.log"
}

run_mpgs_channel_suite() {
  if [[ "${MPGS_LIVE_TEST_ENABLED:-false}" != "true" ]]; then
    echo "MPGS_LIVE_TEST_ENABLED=true is required for the channel Live suite" >&2
    exit 2
  fi
  if [[ -n "${MPGS_CONFIG_FILE:-}" ]]; then
    if [[ ! -f "${MPGS_CONFIG_FILE}" ]]; then
      echo "MPGS_CONFIG_FILE does not reference a regular file" >&2
      exit 2
    fi
  else
    require_value "MPGS_BASE_URL"
    require_value "MPGS_MERCHANT_ID"
    require_value "MPGS_PASSWORD"
  fi
  run_maven_with_evidence \
    "mpgs-channel-live" \
    -Pdev \
    -pl channel-library/payment-channel-library \
    -am \
    -Dtest=MpgsApiClientLiveFlowTests \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test
}

run_openapi_suite() {
  if [[ "${OPENAPI_LIVE_TEST_ENABLED:-false}" != "true" ]]; then
    echo "OPENAPI_LIVE_TEST_ENABLED=true is required for the OpenAPI Live suite" >&2
    exit 2
  fi
  require_value "OPENAPI_LIVE_BASE_URL"
  require_value "OPENAPI_LIVE_JDBC_URL"
  require_value "OPENAPI_LIVE_JDBC_USER"
  require_value "OPENAPI_LIVE_JDBC_PASSWORD"

  run_maven_with_evidence \
    "openapi-mpgs-live" \
    -Pdev \
    -pl service-openapi \
    -am \
    -Dtest=MerchantOpenApiMpgsLiveFlowTests \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dopenapi.live.enabled=true \
    "-Dopenapi.live.risk-block.enabled=${OPENAPI_LIVE_RISK_BLOCK_ENABLED:-false}" \
    "-Dopenapi.live.base-url=${OPENAPI_LIVE_BASE_URL}" \
    "-Dopenapi.live.jdbc-url=${OPENAPI_LIVE_JDBC_URL}" \
    "-Dopenapi.live.jdbc-user=${OPENAPI_LIVE_JDBC_USER}" \
    test
}

run_mysql_suite() {
  if [[ "${RISK_MYSQL_LIVE_ENABLED:-false}" != "true" ]]; then
    echo "RISK_MYSQL_LIVE_ENABLED=true is required for the MySQL Live suite" >&2
    exit 2
  fi
  require_value "RISK_MYSQL_LIVE_URL"
  require_value "RISK_MYSQL_LIVE_USERNAME"
  require_value "RISK_MYSQL_LIVE_PASSWORD"

  run_maven_with_evidence \
    "risk-mysql-live" \
    -Pdev \
    -pl service-risk \
    -am \
    -Dtest=RiskRuntimeMapperMySqlLiveTests \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drisk.mysql.live.enabled=true \
    "-Drisk.mysql.live.url=${RISK_MYSQL_LIVE_URL}" \
    "-Drisk.mysql.live.username=${RISK_MYSQL_LIVE_USERNAME}" \
    test
}

case "${live_suite}" in
  all)
    run_mpgs_channel_suite
    run_openapi_suite
    run_mysql_suite
    ;;
  mpgs-channel)
    run_mpgs_channel_suite
    ;;
  openapi)
    run_openapi_suite
    ;;
  mysql)
    run_mysql_suite
    ;;
  *)
    echo "Unsupported REDIS_EXTERNAL_LIVE_SUITE: ${live_suite}" >&2
    exit 2
    ;;
esac

echo "External Live evidence written to ${evidence_dir}"

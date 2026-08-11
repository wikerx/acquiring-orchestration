#!/usr/bin/env bash

set -euo pipefail

# 该入口只允许 UAT 等价环境，生产环境故障注入必须走独立变更和双人审批流程。
if [[ "${REDIS_UAT_ENVIRONMENT:-}" != "uat" ]]; then
  echo "Refusing UAT readiness tests: REDIS_UAT_ENVIRONMENT must equal uat" >&2
  exit 2
fi
if [[ "${REDIS_UAT_TEST_ACKNOWLEDGED:-false}" != "true" ]]; then
  echo "Refusing UAT readiness tests: set REDIS_UAT_TEST_ACKNOWLEDGED=true after environment approval" >&2
  exit 2
fi
if [[ -z "${REDIS_UAT_CLUSTER_NODES:-}" ]]; then
  echo "REDIS_UAT_CLUSTER_NODES is required and must contain comma-separated host:port nodes" >&2
  exit 2
fi
if [[ -z "${REDIS_UAT_CLUSTER_PASSWORD:-}" ]]; then
  echo "REDIS_UAT_CLUSTER_PASSWORD is required and must be injected as an environment Secret" >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
evidence_root="${REDIS_UAT_EVIDENCE_DIR:-${project_root}/target/redis-readiness/uat}"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_dir="${evidence_root}/${run_id}"

mkdir -p "${evidence_dir}"

# 集成测试只创建带随机 it 前缀的短 TTL Key，并在结束时精确清理，不执行 KEYS 或 FLUSHDB。
(
  cd "${project_root}"
  JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" \
    mvn -Puat \
    -pl service-risk \
    -am \
    -Dtest=DefaultRiskListRuntimeRepositoryClusterIntegrationTests \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Drisk.redis.cluster.integration.enabled=true \
    "-Drisk.redis.cluster.nodes=${REDIS_UAT_CLUSTER_NODES}" \
    "-Drisk.redis.cluster.password=${REDIS_UAT_CLUSTER_PASSWORD}" \
    test
) 2>&1 | tee "${evidence_dir}/redis-cluster-contract.log"

# 可选采集多个受控 Actuator 端点，逗号分隔；认证令牌只进入请求头，不写入日志。
if [[ -n "${REDIS_UAT_PROMETHEUS_URLS:-}" ]]; then
  old_ifs="${IFS}"
  IFS=","
  endpoint_index=0
  for endpoint in ${REDIS_UAT_PROMETHEUS_URLS}; do
    endpoint_index="$((endpoint_index + 1))"
    if [[ "${endpoint}" != https://* && "${endpoint}" != http://* ]]; then
      echo "Prometheus endpoint must use http or https" >&2
      exit 2
    fi
    if [[ -n "${REDIS_UAT_MONITORING_BEARER_TOKEN:-}" ]]; then
      curl --fail --silent --show-error \
        -H "Authorization: Bearer ${REDIS_UAT_MONITORING_BEARER_TOKEN}" \
        "${endpoint}" \
        > "${evidence_dir}/prometheus-${endpoint_index}.txt"
    else
      curl --fail --silent --show-error \
        "${endpoint}" \
        > "${evidence_dir}/prometheus-${endpoint_index}.txt"
    fi
  done
  IFS="${old_ifs}"
fi

echo "UAT readiness evidence written to ${evidence_dir}"
echo "Failover, replica lag, RocketMQ and multi-instance fault injection still require the approved UAT runbook."

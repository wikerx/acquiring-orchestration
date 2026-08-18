#!/usr/bin/env bash

set -euo pipefail

# 本脚本只创建一次性 Redis 6.2.23 Cluster 并运行显式集成测试，不连接共享或生产 Redis。
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
redis_container="acquiring-redis-stage9-cluster-${$}"
redis_image="${REDIS_STAGE9_TEST_IMAGE:-redis:6.2.23}"
cluster_base_port="${REDIS_STAGE9_CLUSTER_PORT_BASE:-17000}"
cluster_end_port="$((cluster_base_port + 5))"
failure_port="${REDIS_STAGE9_FAILURE_PORT:-17999}"
failure_port_2="$((failure_port + 1))"
redis_password="${REDIS_STAGE9_TEST_PASSWORD:-redis-cluster-it-password}"
redis_running="false"

cleanup() {
  if [[ "${redis_running}" == "true" ]]; then
    docker rm -f "${redis_container}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ ! "${cluster_base_port}" =~ ^[1-9][0-9]*$ ]] \
    || [[ "$((cluster_base_port + 10005))" -gt 65535 ]]; then
  echo "REDIS_STAGE9_CLUSTER_PORT_BASE must leave room for six client and cluster-bus ports" >&2
  exit 1
fi
if [[ ! "${failure_port}" =~ ^[1-9][0-9]*$ ]] || [[ "${failure_port_2}" -gt 65535 ]]; then
  echo "REDIS_STAGE9_FAILURE_PORT must leave room for two unreachable Cluster seed ports" >&2
  exit 1
fi
for port in $(seq "${cluster_base_port}" "${cluster_end_port}") \
    "${failure_port}" "${failure_port_2}"; do
  if command -v lsof >/dev/null 2>&1 \
      && lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Redis stage 9 test port ${port} is already in use" >&2
    exit 1
  fi
done

docker run --rm -d \
  --name "${redis_container}" \
  -e CLUSTER_BASE_PORT="${cluster_base_port}" \
  -e REDIS_TEST_PASSWORD="${redis_password}" \
  -p "127.0.0.1:${cluster_base_port}-${cluster_end_port}:${cluster_base_port}-${cluster_end_port}" \
  "${redis_image}" \
  sh -c '
    set -eu
    cluster_end_port=$((CLUSTER_BASE_PORT + 5))
    cluster_port="${CLUSTER_BASE_PORT}"
    while [ "${cluster_port}" -le "${cluster_end_port}" ]; do
      redis-server \
        --port "${cluster_port}" \
        --cluster-enabled yes \
        --cluster-config-file "/tmp/nodes-${cluster_port}.conf" \
        --cluster-node-timeout 5000 \
        --cluster-announce-ip 127.0.0.1 \
        --masterauth "${REDIS_TEST_PASSWORD}" \
        --requirepass "${REDIS_TEST_PASSWORD}" \
        --appendonly no \
        --save "" \
        --protected-mode no \
        --bind 0.0.0.0 \
        --daemonize yes
      cluster_port=$((cluster_port + 1))
    done
    exec tail -f /dev/null
  ' >/dev/null
redis_running="true"

for attempt in {1..30}; do
  if docker exec "${redis_container}" redis-cli \
      --no-auth-warning -a "${redis_password}" -p "${cluster_base_port}" ping >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "Redis stage 9 test container did not become ready" >&2
    exit 1
  fi
  sleep 1
done

cluster_nodes=()
for offset in {0..5}; do
  cluster_nodes+=("127.0.0.1:$((cluster_base_port + offset))")
done
docker exec "${redis_container}" redis-cli \
  --no-auth-warning -a "${redis_password}" \
  --cluster create "${cluster_nodes[@]}" \
  --cluster-replicas 1 \
  --cluster-yes >/dev/null

test_nodes="$(IFS=,; echo "${cluster_nodes[*]}")"
cd "${project_root}"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" \
  mvn -Pdev -pl component-library/component-redis -am \
  -Dtest=RedisIdempotentIntegrationTests,RedisGlobalIdIntegrationTests,RedisCacheGenerationClusterIntegrationTests,RedissonDistributedLockClusterIntegrationTests \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Didempotent.redis.integration.enabled=true \
  -Didempotent.redis.cluster.nodes="${test_nodes}" \
  -Didempotent.redis.cluster.password="${redis_password}" \
  -Dglobal-id.redis.integration.enabled=true \
  -Dglobal-id.redis.cluster.nodes="${test_nodes}" \
  -Dglobal-id.redis.cluster.password="${redis_password}" \
  -Dcache-generation.redis.cluster.integration.enabled=true \
  -Dcache-generation.redis.cluster.nodes="${test_nodes}" \
  -Dcache-generation.redis.cluster.password="${redis_password}" \
  -Dredisson.redis.cluster.integration.enabled=true \
  -Dredisson.redis.cluster.nodes="${test_nodes}" \
  -Dredisson.redis.cluster.password="${redis_password}" \
  test

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" \
  mvn -Pdev -pl component-library/component-redis -am \
  -Dtest=RedisConnectionFailureIntegrationTests \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dredis.failure.integration.enabled=true \
  -Dredis.failure.cluster.nodes="127.0.0.1:${failure_port},127.0.0.1:${failure_port_2}" \
  test

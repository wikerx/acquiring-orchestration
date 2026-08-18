#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
cluster_container="acquiring-risk-redis-cluster-${$}"
redis_image="${RISK_REDIS_CLUSTER_TEST_IMAGE:-redis:6.2.23}"
cluster_base_port="${RISK_REDIS_CLUSTER_TEST_PORT_BASE:-18000}"
cluster_end_port="$((cluster_base_port + 5))"
redis_password="${RISK_REDIS_CLUSTER_TEST_PASSWORD:-redis-cluster-it-password}"

cleanup() {
  docker rm -f "${cluster_container}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --rm -d \
  --name "${cluster_container}" \
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

for attempt in {1..30}; do
  if docker exec "${cluster_container}" redis-cli \
      --no-auth-warning -a "${redis_password}" -p "${cluster_base_port}" ping >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "Redis Cluster nodes did not become ready" >&2
    exit 1
  fi
  sleep 1
done

cluster_nodes=()
for offset in {0..5}; do
  cluster_nodes+=("127.0.0.1:$((cluster_base_port + offset))")
done

docker exec "${cluster_container}" redis-cli \
  --no-auth-warning -a "${redis_password}" --cluster create \
  "${cluster_nodes[@]}" \
  --cluster-replicas 1 \
  --cluster-yes >/dev/null

test_nodes="127.0.0.1:${cluster_base_port},127.0.0.1:$((cluster_base_port + 1)),127.0.0.1:$((cluster_base_port + 2))"
cd "${project_root}"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" \
  mvn -Pdev -pl service-risk -am \
  -Dtest=DefaultRiskListRuntimeRepositoryClusterIntegrationTests \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Drisk.redis.cluster.integration.enabled=true \
  -Drisk.redis.cluster.nodes="${test_nodes}" \
  -Drisk.redis.cluster.password="${redis_password}" \
  test

#!/usr/bin/env bash

set -euo pipefail

# 阶段 10 只使用本脚本创建的一次性 Redis 6.2.23 容器，不连接共享、UAT 或生产 Redis。
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
redis_image="${REDIS_STAGE10_TEST_IMAGE:-redis:6.2.23}"
standalone_container="acquiring-redis-stage10-standalone-${$}"
cluster_container="acquiring-redis-stage10-cluster-${$}"
cluster_base_port="${REDIS_STAGE10_CLUSTER_PORT_BASE:-19000}"
load_requests="${REDIS_STAGE10_LOAD_REQUESTS:-100000}"
evidence_root="${REDIS_STAGE10_EVIDENCE_DIR:-${project_root}/target/redis-readiness/stage10}"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_dir="${evidence_root}/${run_id}"

validate_positive_integer() {
  value_name="$1"
  value="$2"
  maximum="$3"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]] || [[ "${value}" -gt "${maximum}" ]]; then
    echo "${value_name} must be a positive integer no greater than ${maximum}" >&2
    exit 2
  fi
}

validate_positive_integer "REDIS_STAGE10_CLUSTER_PORT_BASE" "${cluster_base_port}" 65000
validate_positive_integer "REDIS_STAGE10_LOAD_REQUESTS" "${load_requests}" 10000000
if [[ "$((cluster_base_port + 5))" -gt 65535 ]]; then
  echo "Redis Cluster port range exceeds 65535" >&2
  exit 2
fi

mkdir -p "${evidence_dir}"

cleanup() {
  docker rm -f "${standalone_container}" >/dev/null 2>&1 || true
  docker rm -f "${cluster_container}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for_redis() {
  container_name="$1"
  port="$2"
  for attempt in $(seq 1 30); do
    if docker exec "${container_name}" redis-cli -p "${port}" ping >/dev/null 2>&1; then
      return 0
    fi
    if [[ "${attempt}" -eq 30 ]]; then
      echo "Redis did not become ready in container ${container_name} on port ${port}" >&2
      return 1
    fi
    sleep 1
  done
}

run_standalone_drills() {
  docker run --rm -d \
    --name "${standalone_container}" \
    "${redis_image}" \
    redis-server \
      --save "" \
      --appendonly no \
      --maxmemory 8mb \
      --maxmemory-policy allkeys-lfu >/dev/null
  wait_for_redis "${standalone_container}" 6379

  # 使用随机测试 Key 和固定 Value 大小触发 LFU 淘汰，验证 evicted_keys 可被 exporter 告警捕获。
  docker exec "${standalone_container}" \
    redis-benchmark -q -t set -n 2000 -r 200000 -d 16384 \
    > "${evidence_dir}/standalone-eviction-load.txt"
  evicted_keys="$(docker exec "${standalone_container}" redis-cli --raw INFO stats \
    | awk -F: '/^evicted_keys:/ {gsub("\r", "", $2); print $2}')"
  if [[ -z "${evicted_keys}" || "${evicted_keys}" -le 0 ]]; then
    echo "Standalone eviction drill did not produce evicted_keys" >&2
    exit 1
  fi

  # 大 Key 只写入一次性实例，随后使用 redis-cli --bigkeys 留存采样证据。
  head -c 1048576 /dev/zero \
    | tr '\0' 'x' \
    | docker exec -i "${standalone_container}" redis-cli -x \
      SET "acquiring:it-stage10:cache:big-key" >/dev/null
  docker exec "${standalone_container}" redis-cli --bigkeys \
    > "${evidence_dir}/standalone-bigkeys.txt"
  if ! grep -q "acquiring:it-stage10:cache:big-key" "${evidence_dir}/standalone-bigkeys.txt"; then
    echo "Big Key drill evidence did not contain the isolated test Key" >&2
    exit 1
  fi

  # LFU 热度计数通过重复读取固定测试 Key形成，不向任何业务 Key 施加负载。
  hot_key="acquiring:it-stage10:cache:hot-key"
  docker exec "${standalone_container}" redis-cli \
    SET "${hot_key}" "hot-value" >/dev/null
  hot_pipeline="${evidence_dir}/hot-key.pipeline"
  hot_key_length="${#hot_key}"
  awk -v key="${hot_key}" -v key_length="${hot_key_length}" 'BEGIN {
    for (i = 0; i < 5000; i++) {
      printf "*2\r\n$3\r\nGET\r\n$%d\r\n%s\r\n", key_length, key
    }
  }' > "${hot_pipeline}"
  docker exec -i "${standalone_container}" redis-cli --pipe \
    < "${hot_pipeline}" \
    > "${evidence_dir}/standalone-hot-load.txt"
  docker exec "${standalone_container}" redis-cli --hotkeys \
    > "${evidence_dir}/standalone-hotkeys.txt"
  if ! grep -q "acquiring:it-stage10:cache:hot-key" "${evidence_dir}/standalone-hotkeys.txt"; then
    echo "Hot Key drill evidence did not contain the isolated test Key" >&2
    exit 1
  fi

  # 无持久化实例重启后缓存 Key 应消失；全局 ID 等持久状态不得使用该部署方式。
  docker exec "${standalone_container}" redis-cli \
    SET "acquiring:it-stage10:cache:restart-key" "ephemeral" >/dev/null
  docker restart "${standalone_container}" >/dev/null
  wait_for_redis "${standalone_container}" 6379
  restart_value="$(docker exec "${standalone_container}" redis-cli --raw \
    GET "acquiring:it-stage10:cache:restart-key")"
  if [[ -n "${restart_value}" ]]; then
    echo "Restart drill expected the no-persistence cache Key to be absent" >&2
    exit 1
  fi

  # 持续负载使用有界请求量，结果同时保留吞吐和延迟摘要。
  docker exec "${standalone_container}" \
    redis-benchmark -q -t set,get -n "${load_requests}" -c 50 -P 16 -r 100000 \
    > "${evidence_dir}/standalone-endurance.txt"
  docker exec "${standalone_container}" redis-cli INFO \
    > "${evidence_dir}/standalone-info-after-load.txt"
}

run_cluster_failover_drill() {
  cluster_end_port="$((cluster_base_port + 5))"
  docker run --rm -d \
    --name "${cluster_container}" \
    -e CLUSTER_BASE_PORT="${cluster_base_port}" \
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
          --cluster-node-timeout 1000 \
          --appendonly no \
          --save "" \
          --protected-mode no \
          --bind 127.0.0.1 \
          --daemonize yes
        cluster_port=$((cluster_port + 1))
      done
      exec tail -f /dev/null
    ' >/dev/null
  wait_for_redis "${cluster_container}" "${cluster_base_port}"

  cluster_nodes=()
  for offset in $(seq 0 5); do
    cluster_nodes+=("127.0.0.1:$((cluster_base_port + offset))")
  done
  docker exec "${cluster_container}" redis-cli --cluster create \
    "${cluster_nodes[@]}" \
    --cluster-replicas 1 \
    --cluster-yes \
    > "${evidence_dir}/cluster-create.txt"

  for attempt in $(seq 1 30); do
    cluster_state="$(docker exec "${cluster_container}" redis-cli -p "${cluster_base_port}" --raw \
      CLUSTER INFO | awk -F: '/^cluster_state:/ {gsub("\r", "", $2); print $2}')"
    if [[ "${cluster_state}" == "ok" ]]; then
      break
    fi
    if [[ "${attempt}" -eq 30 ]]; then
      echo "Redis Cluster did not reach cluster_state=ok" >&2
      exit 1
    fi
    sleep 1
  done

  failover_key="acquiring:it-stage10:cluster:{failover}:state"
  failover_slot="$(docker exec "${cluster_container}" redis-cli -p "${cluster_base_port}" --raw \
    CLUSTER KEYSLOT "${failover_key}")"
  docker exec "${cluster_container}" redis-cli -c -p "${cluster_base_port}" \
    SET "${failover_key}" "before-failover" >/dev/null

  cluster_nodes_before="${evidence_dir}/cluster-nodes-before.txt"
  docker exec "${cluster_container}" redis-cli -p "${cluster_base_port}" CLUSTER NODES \
    > "${cluster_nodes_before}"
  master_address="$(awk -v target_slot="${failover_slot}" '$3 ~ /master/ {
    for (field_number = 9; field_number <= NF; field_number++) {
      split($field_number, slot_range, "-")
      if ((slot_range[2] == "" && slot_range[1] == target_slot) || (slot_range[2] != "" && target_slot >= slot_range[1] && target_slot <= slot_range[2])) {
        print $2
        exit
      }
    }
  }' "${cluster_nodes_before}")"
  master_port="$(echo "${master_address}" | sed -E 's/.*:([0-9]+)@.*/\1/')"
  if [[ -z "${master_port}" || ! "${master_port}" =~ ^[0-9]+$ ]]; then
    echo "Unable to resolve the Redis Cluster master owning slot ${failover_slot}" >&2
    exit 1
  fi

  docker exec "${cluster_container}" redis-cli -p "${master_port}" SHUTDOWN NOSAVE \
    >/dev/null 2>&1 || true

  failover_succeeded="false"
  for attempt in $(seq 1 30); do
    live_port=""
    for offset in $(seq 0 5); do
      candidate_port="$((cluster_base_port + offset))"
      if [[ "${candidate_port}" -ne "${master_port}" ]] \
          && docker exec "${cluster_container}" redis-cli -p "${candidate_port}" ping >/dev/null 2>&1; then
        live_port="${candidate_port}"
        break
      fi
    done
    if [[ -n "${live_port}" ]]; then
      value="$(docker exec "${cluster_container}" redis-cli -c -p "${live_port}" --raw \
        GET "${failover_key}" 2>/dev/null || true)"
      if [[ "${value}" == "before-failover" ]]; then
        failover_succeeded="true"
        docker exec "${cluster_container}" redis-cli -p "${live_port}" CLUSTER NODES \
          > "${evidence_dir}/cluster-nodes-after.txt"
        break
      fi
    fi
    sleep 1
  done
  if [[ "${failover_succeeded}" != "true" ]]; then
    echo "Redis Cluster did not recover the test Key after master shutdown" >&2
    exit 1
  fi
}

run_standalone_drills
run_cluster_failover_drill

{
  echo "evicted_keys=${evicted_keys}"
  echo "load_requests=${load_requests}"
  echo "cluster_failover=passed"
  echo "shared_or_external_redis_accessed=false"
} > "${evidence_dir}/summary.txt"

echo "Redis stage 10 isolated drill evidence written to ${evidence_dir}"

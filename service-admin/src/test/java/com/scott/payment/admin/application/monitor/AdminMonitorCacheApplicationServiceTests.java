package com.scott.payment.admin.application.monitor;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisClusterCommands;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisClusterServerCommands;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorCacheApplicationServiceTests
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : Redis 缓存监控应用服务测试，验证 SCAN、系统参数命名空间、元数据只读和分页上限。
 * @status : create
 */
@Slf4j
class AdminMonitorCacheApplicationServiceTests {

    /**
     * 测试环境统一 Redis Cache 前缀，用于验证环境隔离和物理 Key 拼接。
     */
    private static final String CACHE_PREFIX = "acquiring:test:";

    /**
     * 管理端唯一允许扫描和删除的系统参数缓存前缀。
     */
    private static final String MANAGED_PREFIX = CACHE_PREFIX + "system:config:";

    /**
     * Key 列表必须使用有界 SCAN，不能回退到阻塞式 KEYS。
     */
    @Test
    void shouldScanOnlyManagedPlatformConfigurationKeys() {
        log.info("测试系统参数缓存扫描，关键输入: acquiring:test:system:config 命名空间");
        ScanFixture fixture = scanFixture(List.of(
                List.of(MANAGED_PREFIX + "platform.gateway.base-url"),
                List.of(MANAGED_PREFIX + "platform.checkout.frontend-base-url")
        ));
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys(null, 1, 10);

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat((List<?>) result.get("records")).hasSize(2);
        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(fixture.clusterConnection()).scan(eq(fixture.masterNodes().get(0)), optionsCaptor.capture());
        verify(fixture.clusterConnection()).scan(eq(fixture.masterNodes().get(1)), optionsCaptor.capture());
        assertThat(optionsCaptor.getAllValues())
                .allSatisfy(options -> {
                    assertThat(options.getPattern()).isEqualTo(MANAGED_PREFIX + "*");
                    assertThat(options.getCount()).isEqualTo(100L);
                });
        verify(fixture.template(), never()).keys(anyString());
        fixture.cursors().forEach(cursor -> verify(cursor).close());
        log.info("系统参数缓存扫描测试完成，结果: 逐 Master 使用有界 SCAN 且命名空间正确");
    }

    /**
     * 单次扫描最多检查 1000 个物理 Key，并允许全部全局唯一系统参数进入结果。
     */
    @Test
    void shouldEnforceScanLimitAcrossAllSystemConfigKeys() {
        log.info("测试缓存监控扫描边界，关键输入: 1100 个统一系统参数缓存 Key");
        List<String> keys = new ArrayList<>(1100);
        keys.add(MANAGED_PREFIX + "platform.gateway.base-url");
        keys.add(MANAGED_PREFIX + "platform.checkout.frontend-base-url");
        keys.add(MANAGED_PREFIX + "platform.merchant.frontend-base-url");
        keys.add(MANAGED_PREFIX + "platform.admin.frontend-base-url");
        for (int index = 4; index < 1100; index++) {
            keys.add(MANAGED_PREFIX + "unregistered-" + String.format("%04d", index));
        }
        ScanFixture fixture = scanFixture(List.of(keys, List.of(
                MANAGED_PREFIX + "platform.gateway.base-url"
        )));
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys("*", 1, 500);

        assertThat(result.get("total")).isEqualTo(1000);
        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat((List<?>) result.get("records")).hasSize(100);
        verify(fixture.clusterConnection(), never()).scan(eq(fixture.masterNodes().get(1)), any(ScanOptions.class));
        log.info("缓存监控扫描边界测试完成，结果: 检查上限生效且全部系统参数 Key 均受管");
    }

    /**
     * INFO 必须聚合全部 Master，并跳过 Replica，避免把单节点信息误报为整个 Cluster 状态。
     */
    @Test
    void shouldReadInfoFromEveryClusterMaster() {
        log.info("测试 Redis Cluster INFO 聚合，关键输入: 2 Master + 1 Replica");
        ScanFixture fixture = scanFixture(List.of(List.of(), List.of()));
        RedisClusterServerCommands serverCommands = mock(RedisClusterServerCommands.class);
        when(fixture.clusterConnection().serverCommands()).thenReturn(serverCommands);
        Properties firstInfo = new Properties();
        firstInfo.setProperty("redis_version", "6.2.23");
        Properties secondInfo = new Properties();
        secondInfo.setProperty("role", "master");
        when(serverCommands.info(fixture.masterNodes().get(0))).thenReturn(firstInfo);
        when(serverCommands.info(fixture.masterNodes().get(1))).thenReturn(secondInfo);
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.info();

        assertThat(result)
                .containsEntry("connected", true)
                .containsEntry("deploymentMode", "cluster")
                .containsEntry("masterCount", 2);
        assertThat((Map<String, String>) result.get("info"))
                .containsEntry("redis_version", "6.2.23");
        assertThat((Map<String, Map<String, String>>) result.get("nodes"))
                .containsEntry("127.0.0.1:7001", Map.of("redis_version", "6.2.23"))
                .containsEntry("127.0.0.1:7002", Map.of("role", "master"));
        verify(serverCommands).info(fixture.masterNodes().get(0));
        verify(serverCommands).info(fixture.masterNodes().get(1));
        verify(serverCommands, never()).info(fixture.replicaNode());
        log.info("Redis Cluster INFO 聚合测试完成，结果: 仅遍历两个 Master");
    }

    /**
     * dev 单节点 Redis 必须返回真实连接状态和 INFO 摘要，不能误报为 Cluster 连接失败。
     */
    @Test
    void shouldReadInfoFromStandaloneRedis() {
        log.info("测试单节点 Redis INFO，关键输入: 非 Cluster RedisConnection");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);
        Properties info = new Properties();
        info.setProperty("redis_mode", "standalone");
        info.setProperty("redis_version", "6.2.23");
        when(connection.serverCommands()).thenReturn(serverCommands);
        when(serverCommands.info()).thenReturn(info);
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<?>>any()))
                .thenAnswer(invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        AdminMonitorCacheApplicationService service = service(template);

        Map<String, Object> result = service.info();

        assertThat(result)
                .containsEntry("connected", true)
                .containsEntry("deploymentMode", "standalone")
                .containsEntry("masterCount", 1)
                .containsEntry("info", Map.of("redis_mode", "standalone", "redis_version", "6.2.23"));
        assertThat((Map<?, ?>) result.get("nodes")).hasSize(1);
        log.info("单节点 Redis INFO 测试完成，结果: 连接状态、摘要和节点详情均可用");
    }

    /**
     * Lettuce 可能通过普通 RedisConnection 返回带节点前缀的 Cluster INFO，监控必须恢复节点结构。
     */
    @Test
    void shouldNormalizeAggregatedClusterInfoFromDefaultConnection() {
        log.info("测试聚合 Cluster INFO，关键输入: 普通 RedisConnection 返回两个带节点前缀的 Master 属性");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);
        Properties info = new Properties();
        info.setProperty("node-a.redis_mode", "cluster");
        info.setProperty("node-a.redis_version", "6.2.23");
        info.setProperty("node-b.redis_mode", "cluster");
        info.setProperty("node-b.role", "master");
        when(connection.serverCommands()).thenReturn(serverCommands);
        when(serverCommands.info()).thenReturn(info);
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<?>>any()))
                .thenAnswer(invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        AdminMonitorCacheApplicationService service = service(template);

        Map<String, Object> result = service.info();

        assertThat(result)
                .containsEntry("connected", true)
                .containsEntry("deploymentMode", "cluster")
                .containsEntry("masterCount", 2)
                .containsEntry("info", Map.of("redis_mode", "cluster", "redis_version", "6.2.23"));
        assertThat((Map<String, Map<String, String>>) result.get("nodes"))
                .containsEntry("node-a", Map.of("redis_mode", "cluster", "redis_version", "6.2.23"))
                .containsEntry("node-b", Map.of("redis_mode", "cluster", "role", "master"));
        log.info("聚合 Cluster INFO 测试完成，结果: 节点前缀已拆分为摘要和逐节点结构");
    }

    /**
     * dev 单节点 Redis 的 Key 列表仍必须使用有界 SCAN 并隔离失效门禁命名空间。
     */
    @Test
    void shouldScanManagedKeysFromStandaloneRedis() {
        log.info("测试单节点 Redis SCAN，关键输入: 两个配置 Key 与独立 pending Key");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor(List.of(
                MANAGED_PREFIX + "platform.gateway.base-url",
                CACHE_PREFIX + "system:configPending:config-key-digest",
                MANAGED_PREFIX + "system.name"
        ));
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<?>>any()))
                .thenAnswer(invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
        when(template.type(anyString())).thenReturn(DataType.STRING);
        when(template.getExpire(anyString())).thenReturn(300L);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.size(anyString())).thenReturn(12L);
        AdminMonitorCacheApplicationService service = service(template);

        Map<String, Object> result = service.keys(null, 1, 10);

        assertThat(result).containsEntry("total", 2).containsEntry("truncated", false);
        verify(connection).scan(any(ScanOptions.class));
        verify(template, never()).keys(anyString());
        verify(cursor).close();
        log.info("单节点 Redis SCAN 测试完成，结果: 返回配置数据且不包含 pending Key");
    }

    /**
     * Value 接口只能返回元数据，String 大小必须使用 STRLEN 语义而不是 GET。
     */
    @Test
    void shouldReturnMetadataWithoutReadingCacheValue() {
        log.info("测试平台公开配置缓存元数据只读，关键输入: String Key 与 12 字节 Value");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        String key = MANAGED_PREFIX + "platform.gateway.base-url";
        when(template.type(key)).thenReturn(DataType.STRING);
        when(template.getExpire(key)).thenReturn(300L);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.size(key)).thenReturn(12L);
        AdminMonitorCacheApplicationService service = service(template);

        Map<String, Object> result = service.value(key);

        assertThat(result)
                .containsEntry("key", key)
                .containsEntry("type", "string")
                .containsEntry("ttl", 300L)
                .containsEntry("size", 12L)
                .containsEntry("valueReadable", false)
                .containsEntry("value", null);
        verify(valueOperations).size(key);
        verify(valueOperations, never()).get(anyString());
        log.info("平台公开配置缓存元数据只读测试完成，结果: 返回类型、TTL、大小且未读取 Value");
    }

    /**
     * 查看和删除操作必须拒绝平台配置缓存命名空间之外的 Key。
     */
    @Test
    void shouldRejectKeysOutsideManagedNamespace() {
        log.info("测试缓存监控命名空间隔离，关键输入: 幂等 Key 与 prod 公开配置 Key");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        AdminMonitorCacheApplicationService service = service(template);
        String forbiddenKey = "acquiring:test:idempotency:payment:merchant-order";

        assertThatThrownBy(() -> service.value(forbiddenKey))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("outside the managed");
        assertThatThrownBy(() -> service.delete(forbiddenKey))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.keys("acquiring:prod:system:config:*", 1, 10))
                .isInstanceOf(ServiceException.class);
        verify(template, never()).delete(anyString());
        log.info("缓存监控命名空间隔离测试完成，结果: 非 test 公开配置 Key 均被拒绝");
    }

    /**
     * pending 门禁必须位于独立控制命名空间，普通系统参数键均允许管理。
     */
    @Test
    void shouldKeepInvalidationGateOutsideManagedDataNamespace() {
        log.info("测试缓存监控控制 Key 隔离，关键输入: system:configPending 与普通系统参数");
        String validKey = MANAGED_PREFIX + "platform.gateway.base-url";
        String pendingKey = CACHE_PREFIX + "system:configPending:config-key-digest";
        String regularKey = MANAGED_PREFIX + "system.name";
        ScanFixture fixture = scanFixture(List.of(List.of(validKey, pendingKey), List.of(regularKey)));
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys("*", 1, 10);

        assertThat(result.get("total")).isEqualTo(2);
        assertThat((List<Map<String, Object>>) result.get("records"))
                .extracting(row -> row.get("key"))
                .containsExactly(validKey, regularKey);
        assertThatThrownBy(() -> service.value(pendingKey))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.delete(pendingKey))
                .isInstanceOf(ServiceException.class);
        assertThat(service.value(regularKey).get("key")).isEqualTo(regularKey);
        verify(fixture.template(), never()).delete(anyString());
        log.info("缓存监控控制 Key 隔离测试完成，结果: pending 不可见，普通唯一配置键可管理");
    }

    /**
     * 白名单内的单 Key 删除可以执行，原始 Key 不由请求审计参数记录。
     */
    @Test
    void shouldDeleteManagedPlatformConfigurationKey() {
        log.info("测试系统参数缓存精确删除，关键输入: platform.admin.frontend-base-url");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        String key = MANAGED_PREFIX + "platform.admin.frontend-base-url";
        when(template.delete(key)).thenReturn(true);
        AdminMonitorCacheApplicationService service = service(template);

        assertThat(service.delete(key)).isTrue();

        verify(template).delete(key);
        log.info("系统参数缓存精确删除测试完成，结果: 仅删除统一命名空间内完整物理 Key");
    }

    private AdminMonitorCacheApplicationService service(StringRedisTemplate template) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        PaymentCacheProperties properties = new PaymentCacheProperties();
        properties.setKeyPrefix(CACHE_PREFIX);
        return new AdminMonitorCacheApplicationService(provider, properties);
    }

    /**
     * 创建指定物理 Key 列表的 SCAN 测试夹具。
     *
     * @param masterPhysicalKeys 每个 Master Cursor 按顺序返回的完整 Redis Key
     * @return Redis 模板、Cluster 连接、Master 节点和 Cursor
     */
    private ScanFixture scanFixture(List<List<String>> masterPhysicalKeys) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        RedisClusterCommands clusterCommands = mock(RedisClusterCommands.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        List<RedisClusterNode> masterNodes = List.of(
                clusterNode("127.0.0.1", 7001, RedisClusterNode.Flag.MASTER),
                clusterNode("127.0.0.1", 7002, RedisClusterNode.Flag.MASTER)
        );
        RedisClusterNode replicaNode = clusterNode("127.0.0.1", 7004, RedisClusterNode.Flag.REPLICA);
        when(connection.clusterCommands()).thenReturn(clusterCommands);
        when(clusterCommands.clusterGetNodes()).thenReturn(List.of(
                masterNodes.get(1), replicaNode, masterNodes.get(0)));
        List<Cursor<byte[]>> cursors = new ArrayList<>();
        for (int index = 0; index < masterNodes.size(); index++) {
            List<String> physicalKeys = index < masterPhysicalKeys.size()
                    ? masterPhysicalKeys.get(index)
                    : List.of();
            Cursor<byte[]> cursor = cursor(physicalKeys);
            cursors.add(cursor);
            when(connection.scan(eq(masterNodes.get(index)), any(ScanOptions.class))).thenReturn(cursor);
        }
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<?>>any()))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    return callback.doInRedis(connection);
                });
        when(template.type(anyString())).thenReturn(DataType.STRING);
        when(template.getExpire(anyString())).thenReturn(300L);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.size(anyString())).thenReturn(12L);
        return new ScanFixture(template, connection, masterNodes, replicaNode, cursors);
    }

    /**
     * 创建带角色标记的 Cluster 节点。
     */
    private RedisClusterNode clusterNode(String host, int port, RedisClusterNode.Flag flag) {
        return RedisClusterNode.newRedisClusterNode()
                .listeningAt(host, port)
                .withFlags(Set.of(flag))
                .build();
    }

    /**
     * 创建指定物理 Key 序列的 Cursor。
     */
    private Cursor<byte[]> cursor(List<String> physicalKeys) {
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        List<byte[]> keys = physicalKeys.stream()
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
        AtomicInteger index = new AtomicInteger();
        when(cursor.hasNext()).thenAnswer(invocation -> index.get() < keys.size());
        when(cursor.next()).thenAnswer(invocation -> keys.get(index.getAndIncrement()));
        return cursor;
    }

    private record ScanFixture(StringRedisTemplate template,
                               RedisClusterConnection clusterConnection,
                               List<RedisClusterNode> masterNodes,
                               RedisClusterNode replicaNode,
                               List<Cursor<byte[]>> cursors) {
    }
}

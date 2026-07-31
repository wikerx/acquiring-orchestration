package com.scott.payment.admin.application.monitor;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @description : Redis 缓存监控应用服务测试，验证 SCAN、平台配置白名单、元数据只读和分页上限。
 * @status : create
 */
@Slf4j
class AdminMonitorCacheApplicationServiceTests {

    /**
     * 测试环境统一 Redis Cache 前缀，用于验证环境隔离和物理 Key 拼接。
     */
    private static final String CACHE_PREFIX = "acquiring:test:";

    /**
     * 管理端唯一允许扫描和删除的平台配置缓存前缀。
     */
    private static final String MANAGED_PREFIX = CACHE_PREFIX + "config:public:";

    /**
     * Key 列表必须使用有界 SCAN，不能回退到阻塞式 KEYS。
     */
    @Test
    void shouldScanOnlyManagedPlatformConfigurationKeys() {
        log.info("测试平台公开配置缓存扫描，关键输入: acquiring:test:config:public 命名空间");
        ScanFixture fixture = scanFixture(List.of(
                MANAGED_PREFIX + "platform.gateway.base-url",
                MANAGED_PREFIX + "platform.checkout.frontend-base-url"
        ));
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys(null, 1, 10);

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat((List<?>) result.get("records")).hasSize(2);
        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(fixture.keyCommands()).scan(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getPattern()).isEqualTo(MANAGED_PREFIX + "*");
        assertThat(optionsCaptor.getValue().getCount()).isEqualTo(100L);
        verify(fixture.template(), never()).keys(anyString());
        verify(fixture.cursor()).close();
        log.info("平台公开配置缓存扫描测试完成，结果: 仅使用有界 SCAN 且命名空间正确");
    }

    /**
     * 单次扫描最多检查 1000 个物理 Key，且只返回四个已登记公开配置。
     */
    @Test
    void shouldEnforceScanLimitAndPublicConfigAllowlist() {
        log.info("测试缓存监控扫描边界，关键输入: 4 个公开配置 Key 与 1096 个未登记同前缀 Key");
        List<String> keys = new ArrayList<>(1100);
        keys.add(MANAGED_PREFIX + "platform.gateway.base-url");
        keys.add(MANAGED_PREFIX + "platform.checkout.frontend-base-url");
        keys.add(MANAGED_PREFIX + "platform.merchant.frontend-base-url");
        keys.add(MANAGED_PREFIX + "platform.admin.frontend-base-url");
        for (int index = 4; index < 1100; index++) {
            keys.add(MANAGED_PREFIX + "unregistered-" + String.format("%04d", index));
        }
        ScanFixture fixture = scanFixture(keys);
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys("*", 1, 500);

        assertThat(result.get("total")).isEqualTo(4);
        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat((List<?>) result.get("records")).hasSize(4);
        log.info("缓存监控扫描边界测试完成，结果: 检查上限生效且仅返回 4 个公开配置 Key");
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
        assertThatThrownBy(() -> service.keys("acquiring:prod:config:public:*", 1, 10))
                .isInstanceOf(ServiceException.class);
        verify(template, never()).delete(anyString());
        log.info("缓存监控命名空间隔离测试完成，结果: 非 test 公开配置 Key 均被拒绝");
    }

    /**
     * pending 门禁和同前缀未登记 Key 必须从列表隐藏，并拒绝详情与删除操作。
     */
    @Test
    void shouldHideAndRejectInvalidationGateOrUnregisteredKeys() {
        log.info("测试缓存监控控制 Key 隔离，关键输入: config:public:pending 与未登记配置");
        String validKey = MANAGED_PREFIX + "platform.gateway.base-url";
        String pendingKey = MANAGED_PREFIX + "pending:platform.gateway.base-url";
        String unregisteredKey = MANAGED_PREFIX + "system.name";
        ScanFixture fixture = scanFixture(List.of(validKey, pendingKey, unregisteredKey));
        AdminMonitorCacheApplicationService service = service(fixture.template());

        Map<String, Object> result = service.keys("*", 1, 10);

        assertThat(result.get("total")).isEqualTo(1);
        assertThat((List<Map<String, Object>>) result.get("records"))
                .singleElement()
                .satisfies(row -> assertThat(row.get("key")).isEqualTo(validKey));
        assertThatThrownBy(() -> service.value(pendingKey))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.delete(pendingKey))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.value(unregisteredKey))
                .isInstanceOf(ServiceException.class);
        verify(fixture.template(), never()).delete(anyString());
        log.info("缓存监控控制 Key 隔离测试完成，结果: pending 和未登记 Key 均不可见且不可删除");
    }

    /**
     * 白名单内的单 Key 删除可以执行，原始 Key 不由请求审计参数记录。
     */
    @Test
    void shouldDeleteManagedPlatformConfigurationKey() {
        log.info("测试平台公开配置缓存精确删除，关键输入: platform.admin.frontend-base-url");
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        String key = MANAGED_PREFIX + "platform.admin.frontend-base-url";
        when(template.delete(key)).thenReturn(true);
        AdminMonitorCacheApplicationService service = service(template);

        assertThat(service.delete(key)).isTrue();

        verify(template).delete(key);
        log.info("平台公开配置缓存精确删除测试完成，结果: 仅删除白名单内完整物理 Key");
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
     * @param physicalKeys Cursor 按顺序返回的完整 Redis Key
     * @return Redis 模板、KeyCommands 和 Cursor
     */
    private ScanFixture scanFixture(List<String> physicalKeys) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        List<byte[]> keys = physicalKeys.stream()
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
        AtomicInteger index = new AtomicInteger();
        when(cursor.hasNext()).thenAnswer(invocation -> index.get() < keys.size());
        when(cursor.next()).thenAnswer(invocation -> keys.get(index.getAndIncrement()));
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(template.execute(org.mockito.ArgumentMatchers.<RedisCallback<?>>any()))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    return callback.doInRedis(connection);
                });
        when(template.type(anyString())).thenReturn(DataType.STRING);
        when(template.getExpire(anyString())).thenReturn(300L);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.size(anyString())).thenReturn(12L);
        return new ScanFixture(template, keyCommands, cursor);
    }

    private record ScanFixture(StringRedisTemplate template,
                               RedisKeyCommands keyCommands,
                               Cursor<byte[]> cursor) {
    }
}

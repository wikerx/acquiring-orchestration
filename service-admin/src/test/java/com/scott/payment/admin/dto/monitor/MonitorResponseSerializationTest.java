package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertSourceMetrics;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.CpuInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存、服务器和在线会话监控响应的 JSON 兼容性测试。
 */
class MonitorResponseSerializationTest {

    /**
     * 使用生产同类 Jackson 模块验证响应字段和空值契约。
     */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * Redis 不可用时只返回既有的连接状态和安全错误摘要。
     */
    @Test
    void cacheInfoShouldOmitUnavailableTopologyFields() {
        CacheInfoResponse response = new CacheInfoResponse();
        response.setConnected(false);
        response.setMessage("RedisTemplate unavailable");

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.fieldNames()).toIterable().containsExactly("connected", "message");
        assertThat(json.path("connected").asBoolean()).isFalse();
        assertThat(json.path("message").asText()).isEqualTo("RedisTemplate unavailable");
    }

    /**
     * Key 列表不得扩展详情字段，详情响应必须继续显式返回不可读标志和空值。
     */
    @Test
    void cacheKeyResponsesShouldPreserveListAndDetailContracts() {
        CacheKeyResponse key = new CacheKeyResponse();
        key.setKey("acquiring:test:system:config:platform.gateway.base-url");
        key.setType("string");
        key.setTtl(300L);
        key.setSize(12L);
        CacheKeyPageResponse page = new CacheKeyPageResponse();
        page.setRecords(List.of(key));
        page.setTotal(1);
        page.setTruncated(false);

        CacheKeyDetailResponse detail = new CacheKeyDetailResponse();
        detail.setKey(key.getKey());
        detail.setType(key.getType());
        detail.setTtl(key.getTtl());
        detail.setSize(key.getSize());
        detail.setValueReadable(false);
        detail.setValue(null);

        JsonNode pageJson = objectMapper.valueToTree(page);
        JsonNode detailJson = objectMapper.valueToTree(detail);

        assertThat(pageJson.path("records").get(0).fieldNames()).toIterable()
                .containsExactly("key", "type", "ttl", "size");
        assertThat(pageJson.path("total").asInt()).isEqualTo(1);
        assertThat(pageJson.path("truncated").asBoolean()).isFalse();
        assertThat(detailJson.path("valueReadable").asBoolean()).isFalse();
        assertThat(detailJson.has("value")).isTrue();
        assertThat(detailJson.path("value").isNull()).isTrue();
    }

    /**
     * 在线会话标识必须保持字符串，避免浏览器处理 BIGINT 时发生精度损失。
     */
    @Test
    void onlineUserShouldUseLowerCamelCaseAndStringSessionId() {
        OnlineUserResponse onlineUser = new OnlineUserResponse();
        onlineUser.setSessionId(new BigInteger("9007199254740993").toString());
        onlineUser.setUserName("Alice");

        JsonNode json = objectMapper.valueToTree(onlineUser);

        assertThat(json.path("sessionId").isTextual()).isTrue();
        assertThat(json.path("sessionId").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("userName").asText()).isEqualTo("Alice");
        assertThat(json.has("session_id")).isFalse();
    }

    /**
     * 服务器监控继续使用原有的分组字段和 lowerCamelCase 子字段。
     */
    @Test
    void serverMonitorShouldPreserveNestedFieldNames() {
        CpuInfo cpu = new CpuInfo();
        cpu.setAvailableProcessors(8);
        cpu.setSystemLoadAverage(1.25D);
        ServerMonitorResponse response = new ServerMonitorResponse();
        response.setCpu(cpu);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("cpu").path("availableProcessors").asInt()).isEqualTo(8);
        assertThat(json.path("cpu").path("systemLoadAverage").asDouble()).isEqualTo(1.25D);
        assertThat(json.has("disk")).isFalse();
    }

    /**
     * 告警来源指标保持历史 snake_case 字段名，并省略另一来源不适用的字段。
     */
    @Test
    void alertSourceMetricsShouldPreserveSparseSnakeCaseContract() {
        AlertSourceMetrics metrics = new AlertSourceMetrics();
        metrics.setChannelCode("SCOTT_CHANNEL_A");
        metrics.setSuccessRate(new BigDecimal("98.75"));

        JsonNode json = objectMapper.valueToTree(metrics);

        assertThat(json.path("channel_code").asText()).isEqualTo("SCOTT_CHANNEL_A");
        assertThat(json.path("success_rate").decimalValue()).isEqualByComparingTo("98.75");
        assertThat(json.has("channelCode")).isFalse();
        assertThat(json.has("source_layer")).isFalse();
    }
}

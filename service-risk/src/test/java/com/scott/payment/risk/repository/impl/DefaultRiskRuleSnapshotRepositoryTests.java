package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.config.RiskRuleCacheMode;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskRuleSnapshot;
import com.scott.payment.risk.domain.RiskRuleSnapshotRow;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskRuleSnapshotRepositoryTests
 * @date : 2026-07-31 15:52
 * @email : scott_x@163.com
 * @description : 风控完整常驻快照契约测试，覆盖 Hash、代际重建、有效空集合、短 Key 与无 TTL 写入
 * @status : create
 */
@Slf4j
class DefaultRiskRuleSnapshotRepositoryTests {

    /**
     * 精确名单 Hash 的 generation 与目标字段均命中时必须直接返回，不得访问主库点查。
     */
    @Test
    void shouldReadExactListMatchFromPermanentHashSnapshot() {
        log.info("测试精确名单 Hash 快照，关键输入: generation 匹配、卡指纹哈希字段命中");
        Fixture fixture = fixture();
        RiskRuleSnapshot metadata = RiskRuleSnapshot.rows("g-snapshot", List.of());
        metadata.setCount(1);
        RiskRuleSnapshotRow row = listRow(91L, "fingerprint-hash");
        String expectedKey = "acquiring:test:risk:black:cardFingerprint:M202607290001";
        when(fixture.hashOperations().get(expectedKey, "@meta"))
                .thenReturn(JsonUtils.toJsonString(metadata));
        when(fixture.hashOperations().get(expectedKey, "fingerprint-hash"))
                .thenReturn(JsonUtils.toJsonString(row));
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setMatchValueHash("fingerprint-hash");

        assertThat(fixture.repository().findListMatch(
                RiskListFunction.BLACK_CARD_FINGERPRINT,
                "M202607290001",
                lookupValue
        )).get().extracting(match -> match.getRuleId()).isEqualTo(91L);

        verify(fixture.mapper(), never()).selectHashMatch(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.mapper(), never()).selectActiveHashSnapshotRows(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(Integer.class));
        log.info("精确名单 Hash 快照测试完成，结果: 短 Key 命中且未访问数据库");
    }

    /**
     * 序列化区间快照的 generation 过期时必须从主库加载完整集合并以无 TTL String 重建。
     */
    @Test
    void shouldRebuildStaleIpRangeSnapshotWithoutTtl() {
        log.info("测试 IP 区间快照代际重建，关键输入: 缓存 g-old、当前 g-snapshot");
        Fixture fixture = fixture();
        String expectedKey = "acquiring:test:risk:black:ip:M202607290001";
        RiskRuleSnapshot stale = RiskRuleSnapshot.rows("g-old", List.of());
        when(fixture.valueOperations().get(expectedKey)).thenReturn(JsonUtils.toJsonString(stale));
        RiskRuleSnapshotRow row = listRow(92L, null);
        row.setIpVersion("IPV4");
        row.setMatchValueStartNumber(new BigDecimal("167772160"));
        row.setMatchValueEndNumber(new BigDecimal("184549375"));
        when(fixture.mapper().selectActiveIpRangeSnapshotRows(
                "risk_black_ip",
                "BLACK",
                "ip",
                "IP地址/区间黑名单",
                "ip",
                "M202607290001",
                5001
        )).thenReturn(List.of(row));
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setIpVersion("IPV4");
        lookupValue.setNumericValue(new BigDecimal("167772161"));

        assertThat(fixture.repository().findListMatch(
                RiskListFunction.BLACK_IP,
                "M202607290001",
                lookupValue
        )).get().extracting(match -> match.getRuleId()).isEqualTo(92L);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.valueOperations()).set(org.mockito.ArgumentMatchers.eq(expectedKey), valueCaptor.capture());
        RiskRuleSnapshot rebuilt = JsonUtils.parseObject(valueCaptor.getValue(), RiskRuleSnapshot.class);
        assertThat(rebuilt.getGeneration()).isEqualTo("g-snapshot");
        assertThat(rebuilt.isLoaded()).isTrue();
        assertThat(rebuilt.getCount()).isEqualTo(1);
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq(expectedKey),
                anyString(),
                any(java.time.Duration.class)
        );
        log.info("IP 区间快照代际重建测试完成，结果: 主库完整集合以无 TTL 短 Key 写入");
    }

    /**
     * 来源网址有效空快照必须回答“未配置”，不能把空集合解释为缓存缺失后查询数据库。
     */
    @Test
    void shouldTreatLoadedEmptySourceSnapshotAsAuthoritativeMiss() {
        log.info("测试来源网址有效空快照，关键输入: loaded=true、count=0");
        Fixture fixture = fixture();
        String expectedKey = "acquiring:test:risk:source:M202607290001";
        when(fixture.valueOperations().get(expectedKey))
                .thenReturn(JsonUtils.toJsonString(RiskRuleSnapshot.rows("g-snapshot", List.of())));
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setSourceHost("checkout.example.com");

        assertThat(fixture.repository().findSourceUrlRestrictionMiss(
                "M202607290001",
                lookupValue
        )).isEmpty();

        verify(fixture.mapper(), never()).countActiveSourceUrlRules(anyString());
        verify(fixture.mapper(), never()).selectActiveSourceUrlSnapshotRows(anyString(), any(Integer.class));
        log.info("来源网址有效空快照测试完成，结果: 未穿透数据库且判定为未配置");
    }

    /**
     * 超容量快照必须在短 TTL 内直接旁路到精确查询，不能为每个请求重复加载上限加一行。
     */
    @Test
    void shouldBypassRepeatedSnapshotLoadAfterCapacityExceeded() {
        log.info("测试超容量快照旁路，关键输入: 快照上限 1、数据库返回 2 行、连续查询两次");
        Fixture fixture = fixture(1, activeGeneration("g-snapshot"));
        String snapshotKey = "acquiring:test:risk:black:ip:M202607290001";
        String bypassKey = snapshotKey + ":capacity-bypass";
        when(fixture.valueOperations().get(snapshotKey)).thenReturn(null);
        when(fixture.valueOperations().get(bypassKey)).thenReturn(null, "g-snapshot");
        List<RiskRuleSnapshotRow> oversizedRows = List.of(listRow(101L, null), listRow(102L, null));
        when(fixture.mapper().selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        )).thenReturn(oversizedRows);
        RiskRuleSnapshotRow exactMatch = listRow(103L, null);
        when(fixture.mapper().selectIpRangeMatch(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", "IPV4", new BigDecimal("167772161")
        )).thenReturn(exactMatch);
        RiskRuntimeLookupValue lookupValue = ipLookupValue();

        assertThat(fixture.repository().findListMatch(
                RiskListFunction.BLACK_IP, "M202607290001", lookupValue
        )).get().extracting(match -> match.getRuleId()).isEqualTo(103L);
        assertThat(fixture.repository().findListMatch(
                RiskListFunction.BLACK_IP, "M202607290001", lookupValue
        )).get().extracting(match -> match.getRuleId()).isEqualTo(103L);

        verify(fixture.mapper(), times(1)).selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        );
        verify(fixture.mapper(), times(2)).selectIpRangeMatch(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", "IPV4", new BigDecimal("167772161")
        );
        verify(fixture.valueOperations()).set(bypassKey, "g-snapshot", Duration.ofSeconds(30));
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq(snapshotKey), anyString());
        log.info("超容量快照旁路测试完成，结果: 完整集合只加载一次且每次均执行精确回源");
    }

    /**
     * 容量旁路只对创建它的 generation 有效，规则发布后必须重新探测完整快照容量。
     */
    @Test
    void shouldInvalidateCapacityBypassWhenGenerationChanges() {
        log.info("测试超容量旁路代际失效，关键输入: 旁路 g-old、当前 g-new");
        Fixture fixture = fixture(1, activeGeneration("g-new"));
        String snapshotKey = "acquiring:test:risk:black:ip:M202607290001";
        String bypassKey = snapshotKey + ":capacity-bypass";
        when(fixture.valueOperations().get(snapshotKey)).thenReturn(null);
        when(fixture.valueOperations().get(bypassKey)).thenReturn("g-old");
        when(fixture.mapper().selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        )).thenReturn(List.of(listRow(111L, null), listRow(112L, null)));

        fixture.repository().findListMatch(
                RiskListFunction.BLACK_IP, "M202607290001", ipLookupValue()
        );

        verify(fixture.mapper()).selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        );
        verify(fixture.valueOperations()).set(bypassKey, "g-new", Duration.ofSeconds(30));
        log.info("超容量旁路代际失效测试完成，结果: 新代际重新探测并刷新旁路标记");
    }

    /**
     * Redis 无法读取旁路标记时必须重新加载容量并执行精确 SQL，不能将缓存故障解释为未命中。
     */
    @Test
    void shouldUseExactDatabaseQueryWhenCapacityBypassRedisReadFails() {
        log.info("测试超容量旁路 Redis 异常，关键输入: 旁路 Key 读取抛出运行时异常");
        Fixture fixture = fixture(1, activeGeneration("g-snapshot"));
        String snapshotKey = "acquiring:test:risk:black:ip:M202607290001";
        String bypassKey = snapshotKey + ":capacity-bypass";
        when(fixture.valueOperations().get(snapshotKey)).thenReturn(null);
        when(fixture.valueOperations().get(bypassKey)).thenThrow(new IllegalStateException("redis unavailable"));
        when(fixture.mapper().selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        )).thenReturn(List.of(listRow(121L, null), listRow(122L, null)));
        RiskRuleSnapshotRow exactMatch = listRow(123L, null);
        when(fixture.mapper().selectIpRangeMatch(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", "IPV4", new BigDecimal("167772161")
        )).thenReturn(exactMatch);

        assertThat(fixture.repository().findListMatch(
                RiskListFunction.BLACK_IP, "M202607290001", ipLookupValue()
        )).get().extracting(match -> match.getRuleId()).isEqualTo(123L);

        verify(fixture.mapper()).selectActiveIpRangeSnapshotRows(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", 2
        );
        verify(fixture.mapper()).selectIpRangeMatch(
                "risk_black_ip", "BLACK", "ip", "IP地址/区间黑名单", "ip",
                "M202607290001", "IPV4", new BigDecimal("167772161")
        );
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq(snapshotKey), anyString());
        log.info("超容量旁路 Redis 异常测试完成，结果: 风控判断保持数据库精确回源语义");
    }

    /**
     * 创建 SNAPSHOT 模式测试夹具，所有 Redis 操作均使用字符串序列化接口。
     *
     * @return Mapper、Redis 操作接口和仓储
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        return fixture(5000, activeGeneration("g-snapshot"));
    }

    /**
     * 创建指定容量和 generation 的 SNAPSHOT 模式测试夹具。
     *
     * @param maxRows        单快照最大行数
     * @param generationStore 当前规则代际存储
     * @return Mapper、Redis 操作接口和仓储
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture(int maxRows, RedisCacheGenerationStore generationStore) {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        RedisStringService legacyRedis = mock(RedisStringService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setRuleCacheMode(RiskRuleCacheMode.SNAPSHOT);
        properties.setRuleSnapshotMaxRows(maxRows);
        properties.setRuleSnapshotMaxCharacters(5 * 1024 * 1024);
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:test");
        DefaultRiskListRuntimeRepository repository = new DefaultRiskListRuntimeRepository(
                provider(mapper),
                provider(legacyRedis),
                provider(generationStore),
                provider(redisTemplate),
                provider(null),
                provider(null),
                properties,
                redisProperties
        );
        return new Fixture(mapper, valueOperations, hashOperations, repository);
    }

    /**
     * 创建始终返回指定活跃 generation 的缓存代际存储。
     *
     * @param generation 当前规则代际
     * @return generation 存储 mock
     */
    private RedisCacheGenerationStore activeGeneration(String generation) {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("risk-runtime-rule"))
                .thenReturn(RedisCacheGenerationState.active(generation));
        return generationStore;
    }

    /**
     * 构造命中测试网段的 IPv4 查询值。
     *
     * @return 规范化 IP 区间查询值
     */
    private RiskRuntimeLookupValue ipLookupValue() {
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setIpVersion("IPV4");
        lookupValue.setNumericValue(new BigDecimal("167772161"));
        return lookupValue;
    }

    /**
     * 构造精确或区间名单快照行。
     *
     * @param ruleId         规则主键
     * @param matchValueHash 精确匹配哈希，区间规则允许为空
     * @return 黑名单快照行
     */
    private RiskRuleSnapshotRow listRow(long ruleId, String matchValueHash) {
        RiskRuleSnapshotRow row = new RiskRuleSnapshotRow();
        row.setRuleId(ruleId);
        row.setModuleType("BLACK");
        row.setFunctionCode("cardFingerprint");
        row.setFunctionName("卡指纹黑名单");
        row.setHitElement("cardFingerprint");
        row.setHitValueMasked("HASH");
        row.setRiskLevel("HIGH");
        row.setDecisionAction("REJECT");
        row.setDecisionReason("test rule");
        row.setMerchantScope("MERCHANT");
        row.setMerchantId("M202607290001");
        row.setMatchValueHash(matchValueHash);
        return row;
    }

    /**
     * 创建返回指定对象的可选依赖 Provider。
     *
     * @param value 依赖对象，允许为空
     * @param <T>   依赖类型
     * @return ObjectProvider mock
     */
    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    /**
     * 快照仓储测试依赖。
     *
     * @param mapper          风控主库 Mapper
     * @param valueOperations Redis String 操作接口
     * @param hashOperations  Redis Hash 操作接口
     * @param repository      待测仓储
     */
    private record Fixture(RiskRuntimeMapper mapper,
                           ValueOperations<String, String> valueOperations,
                           HashOperations<String, Object, Object> hashOperations,
                           DefaultRiskListRuntimeRepository repository) {
    }
}

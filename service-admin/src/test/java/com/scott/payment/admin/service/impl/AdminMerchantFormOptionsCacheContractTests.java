package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.application.base.AdminBaseMccApplicationService;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.mcc.entity.SharedMccCodeDO;
import com.scott.payment.component.db.mcc.entity.SharedMccLevel1DO;
import com.scott.payment.component.db.mcc.entity.SharedMccLevel2DO;
import com.scott.payment.component.db.mcc.mapper.SharedMccCodeMapper;
import com.scott.payment.component.db.mcc.mapper.SharedMccLevel1Mapper;
import com.scott.payment.component.db.mcc.mapper.SharedMccLevel2Mapper;
import com.scott.payment.component.db.mcc.model.MccOptionSnapshot;
import com.scott.payment.component.db.mcc.service.MccOptionCacheInvalidator;
import com.scott.payment.component.db.mcc.service.MccOptionCacheReader;
import com.scott.payment.component.redis.cache.PaymentCacheRegistry;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantFormOptionsCacheContractTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 商户表单组合公共 MCC、国家和币种缓存的边界契约测试
 * @status : update
 */
@Slf4j
class AdminMerchantFormOptionsCacheContractTests {

    /** 商户表单只组合公共快照且不创建聚合缓存，未命中重建入口显式使用主库。 */
    @Test
    void shouldComposePublicCachesWithoutAdminAggregateCache() throws Exception {
        Method method = AdminMerchantInfoServiceImpl.class.getMethod("getFormOptions");

        assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class)).isNull();
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(PaymentCacheRegistry.defaultTtls())
                .containsEntry(PaymentCacheNames.MCC_OPTIONS, Duration.ZERO)
                .containsEntry(PaymentCacheNames.ISO_COUNTRY, Duration.ZERO)
                .containsEntry(PaymentCacheNames.ISO_CURRENCY, Duration.ZERO);
    }

    /** 公共 MCC 快照必须从主库重建，避免变更后由从库延迟回填旧值。 */
    @Test
    void shouldRebuildMccOptionsFromMaster() throws Exception {
        Method method = MccOptionCacheReader.class.getMethod("listOptions");
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }

    /** MCC 管理服务必须显式依赖可靠失效器，写方法不再使用事务内直接 CacheEvict。 */
    @Test
    void shouldUseReliableMccInvalidatorForEveryMutation() throws Exception {
        Constructor<?> constructor = AdminBaseMccApplicationService.class.getConstructors()[0];
        assertThat(List.of(constructor.getParameterTypes())).contains(MccOptionCacheInvalidator.class);

        assertMutation("saveCategory", MccRequests.MccCategorySaveRequest.class);
        assertMutation("deleteCategory", MccRequests.MccDeleteRequest.class);
        assertMutation("updateStatus", MccRequests.MccStatusUpdateRequest.class);
        assertMutation("createCode", MccRequests.MccCodeSaveRequest.class);
        assertMutation("updateCode", MccRequests.MccCodeSaveRequest.class);
        assertMutation("deleteCode", MccRequests.MccDeleteRequest.class);
    }

    /** 公共 MCC 快照必须能够通过受控 Redis 类型白名单往返序列化。 */
    @Test
    void shouldRoundTripMccSnapshotThroughRedisSerializer() {
        MccOptionSnapshot leaf = option("5999", "5999 - Miscellaneous Retail Stores");
        MccOptionSnapshot root = option("L1:1", "Retail");
        root.setChildren(new ArrayList<>(List.of(leaf)));

        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();
        Object restored = serializer.deserialize(serializer.serialize(root));

        assertThat(restored).isInstanceOf(MccOptionSnapshot.class);
        MccOptionSnapshot restoredOptions = (MccOptionSnapshot) restored;
        assertThat(restoredOptions.getChildren()).singleElement()
                .satisfies(item -> assertThat(item.getValue()).isEqualTo("5999"));
    }

    /** 公共 MCC 快照必须写入统一短 Key，并使用无 TTL 的字符串写入方法。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteMccSnapshotToPermanentConciseKey() {
        log.info("测试 MCC 公共缓存物理键，关键输入: dev 环境三级启用选项");
        SharedMccLevel1Mapper level1Mapper = mock(SharedMccLevel1Mapper.class);
        SharedMccLevel2Mapper level2Mapper = mock(SharedMccLevel2Mapper.class);
        SharedMccCodeMapper codeMapper = mock(SharedMccCodeMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider = mock(ObjectProvider.class);
        ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider = mock(ObjectProvider.class);
        ObjectProvider<ManagedCacheInvalidationCoordinator> coordinatorProvider = mock(ObjectProvider.class);

        PaymentRedisProperties keyResolver = new PaymentRedisProperties();
        keyResolver.setKeyPrefix("acquiring:dev");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(keyResolverProvider.getIfAvailable()).thenReturn(keyResolver);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(level1Mapper.selectList(any())).thenReturn(List.of(level1Row()));
        when(level2Mapper.selectList(any())).thenReturn(List.of(level2Row()));
        when(codeMapper.selectList(any())).thenReturn(List.of(codeRow()));

        MccOptionCacheReader reader = new MccOptionCacheReader(
                level1Mapper,
                level2Mapper,
                codeMapper,
                redisProvider,
                keyResolverProvider,
                invalidationGuardProvider,
                coordinatorProvider
        );

        assertThat(reader.listOptions()).singleElement()
                .satisfies(level1 -> assertThat(level1.getChildren()).singleElement()
                        .satisfies(level2 -> assertThat(level2.getChildren()).singleElement()
                                .satisfies(code -> assertThat(code.getValue()).isEqualTo("5999"))));
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("acquiring:dev:mcc:options:all"),
                anyString());
        log.info("MCC 公共缓存物理键测试完成，结果: 使用无 TTL 的 acquiring:dev:mcc:options:all");
    }

    /** 校验 MCC 写入口的主库、事务和无直接缓存删除声明。 */
    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminBaseMccApplicationService.class.getMethod(methodName, parameterTypes);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(dataSource).as(method.toString()).isNotNull();
        assertThat(dataSource.value()).as(method.toString()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).as(method.toString()).isNotNull();
        assertThat(transactional.rollbackFor()).as(method.toString()).contains(Exception.class);
        assertThat(AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class)).isNull();
    }

    /** 创建 MCC 缓存测试节点。 */
    private MccOptionSnapshot option(String value, String label) {
        MccOptionSnapshot option = new MccOptionSnapshot();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }

    /** 构造启用的 MCC 一级分类测试记录。 */
    private SharedMccLevel1DO level1Row() {
        SharedMccLevel1DO row = new SharedMccLevel1DO();
        row.setId(1L);
        row.setLevel1Code("RETAIL");
        row.setNameCn("零售");
        row.setNameEn("Retail");
        return row;
    }

    /** 构造启用的 MCC 二级分类测试记录。 */
    private SharedMccLevel2DO level2Row() {
        SharedMccLevel2DO row = new SharedMccLevel2DO();
        row.setId(2L);
        row.setLevel1Id(1L);
        row.setLevel2Code("MISC_RETAIL");
        row.setNameCn("其他零售");
        row.setNameEn("Miscellaneous retail");
        return row;
    }

    /** 构造启用的四位 MCC 测试记录。 */
    private SharedMccCodeDO codeRow() {
        SharedMccCodeDO row = new SharedMccCodeDO();
        row.setLevel2Id(2L);
        row.setMccCode("5999");
        row.setNameCn("其他零售店");
        row.setNameEn("Miscellaneous Retail Stores");
        return row;
    }
}

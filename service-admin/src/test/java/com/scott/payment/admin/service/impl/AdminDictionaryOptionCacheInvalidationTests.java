package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.converter.DictConverter;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.mapper.SysDictTypeMapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.dictionary.service.DictionaryOptionCacheReader;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictionaryOptionCacheInvalidationTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 管理端字典类型和字典项全部写入口的共享下拉缓存失效契约测试
 * @status : create
 */
class AdminDictionaryOptionCacheInvalidationTests {

    /** 初始化 MyBatis-Plus Lambda 查询所需实体元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, SysDictTypeDO.class);
        TableInfoHelper.initTableInfo(assistant, SysDictDataDO.class);
    }

    /** 所有字典数据库写入口必须固定使用主库和回滚事务。 */
    @Test
    void shouldRunAllDictionaryMutationsInsideMasterTransactions() throws Exception {
        assertMutation("saveDictType", SysDictTypeSaveRequest.class);
        assertMutation("deleteDictType", String.class);
        assertMutation("saveDictData", SysDictDataSaveRequest.class);
        assertMutation("updateDictDataById", Long.class, SysDictDataSaveRequest.class);
        assertMutation("deleteDictData", String.class, String.class, String.class);
        assertMutation("deleteDictDataById", Long.class);
    }

    /** 新增字典项时新旧分组相同，只允许删除一次精确缓存键。 */
    @Test
    void shouldEvictOneExactKeyAfterDictionaryItemCreation() {
        Fixture fixture = fixture();
        when(fixture.dataMapper().selectOne(any())).thenReturn(null);

        fixture.service().saveDictData(dataRequest("payment_type", "CARD", "zh-CN"));

        verify(fixture.invalidationCoordinator(), times(1)).prepare(
                PaymentCacheNames.SYSTEM_DICT_OPTIONS,
                "payment_type:zh-CN"
        );
    }

    /** 字典项语言分组发生变化时必须同时删除旧键和新键。 */
    @Test
    void shouldEvictOldAndNewKeysAfterDictionaryItemLocaleChange() {
        Fixture fixture = fixture();
        SysDictDataDO existing = dictionaryData(8L, "payment_type", "CARD", "zh-CN");
        when(fixture.dataMapper().selectOne(any())).thenReturn(existing);

        fixture.service().updateDictDataById(
                existing.getId(), dataRequest("payment_type", "CARD", "en-US"));

        verify(fixture.invalidationCoordinator()).prepare(
                PaymentCacheNames.SYSTEM_DICT_OPTIONS,
                "payment_type:zh-CN"
        );
        verify(fixture.invalidationCoordinator()).prepare(
                PaymentCacheNames.SYSTEM_DICT_OPTIONS,
                "payment_type:en-US"
        );
    }

    /** 类型变更和人工刷新必须清空有限且规模较小的字典下拉缓存。 */
    @Test
    void shouldClearOptionCacheAfterTypeMutationAndManualRefresh() {
        Fixture fixture = fixture();
        when(fixture.typeMapper().selectOne(any())).thenReturn(null);
        SysDictTypeSaveRequest request = new SysDictTypeSaveRequest();
        request.setDictType("payment_type");
        request.setDictName("支付类型");
        request.setOperator("unit-test");

        fixture.service().saveDictType(request);
        fixture.service().refreshOptionCache();

        verify(fixture.cache(), times(2)).clear();
    }

    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminDictServiceImpl.class.getMethod(methodName, parameterTypes);
        DS dataSource = method.getAnnotation(DS.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(dataSource).as(method.toString()).isNotNull();
        assertThat(dataSource.value()).as(method.toString()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).as(method.toString()).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private Fixture fixture() {
        SysDictTypeMapper typeMapper = mock(SysDictTypeMapper.class);
        SysDictDataMapper dataMapper = mock(SysDictDataMapper.class);
        Cache cache = mock(Cache.class);
        CacheManager cacheManager = mock(CacheManager.class);
        ManagedCacheInvalidationCoordinator invalidationCoordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        when(cacheManager.getCache(PaymentCacheNames.SYSTEM_DICT_OPTIONS)).thenReturn(cache);
        AdminDictServiceImpl service = new AdminDictServiceImpl(
                typeMapper,
                dataMapper,
                mock(DictConverter.class),
                mock(DictionaryOptionCacheReader.class),
                cacheManager,
                invalidationCoordinator
        );
        return new Fixture(service, typeMapper, dataMapper, cache, invalidationCoordinator);
    }

    private SysDictDataSaveRequest dataRequest(String dictType, String dictValue, String locale) {
        SysDictDataSaveRequest request = new SysDictDataSaveRequest();
        request.setDictType(dictType);
        request.setDictValue(dictValue);
        request.setDictLabel("Card payment");
        request.setLocale(locale);
        request.setStatus(1);
        request.setOperator("unit-test");
        return request;
    }

    private SysDictDataDO dictionaryData(Long id, String dictType, String dictValue, String locale) {
        SysDictDataDO row = new SysDictDataDO();
        row.setId(id);
        row.setDictType(dictType);
        row.setDictValue(dictValue);
        row.setDictLabel("Card payment");
        row.setLocale(locale);
        row.setStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private record Fixture(AdminDictServiceImpl service,
                           SysDictTypeMapper typeMapper,
                           SysDictDataMapper dataMapper,
                           Cache cache,
                           ManagedCacheInvalidationCoordinator invalidationCoordinator) {
    }
}

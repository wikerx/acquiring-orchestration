package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.application.cache.MerchantSecurityCacheInvalidationCoordinator;
import com.scott.payment.admin.converter.ConfigConverter;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.entity.SysConfigDO;
import com.scott.payment.admin.mapper.SysConfigMapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端平台公开配置可靠缓存失效测试。
 */
@Slf4j
class AdminConfigServiceImplTests {

    /**
     * 验证保存公开配置时先按规范化配置键登记永久缓存失效意图。
     */
    @Test
    void shouldPrepareReliableInvalidationWhenSavingPublicConfig() {
        log.info("测试公开配置保存失效，关键输入: 带首尾空白的 platform.gateway.base-url");
        Fixture fixture = fixture();
        when(fixture.mapper().selectOne(any())).thenReturn(null);
        when(fixture.converter().toDTO(any())).thenReturn(new SysConfigDTO());
        SysConfigSaveRequest request = request(" platform.gateway.base-url ");

        fixture.service().saveConfig(request);

        verify(fixture.coordinator()).prepare(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.gateway.base-url"
        );
        verify(fixture.mapper()).insert(argThat((SysConfigDO row) ->
                "platform.gateway.base-url".equals(row.getConfigKey())
        ));
        assertThat(request.getConfigKey()).isEqualTo("platform.gateway.base-url");
        log.info("公开配置保存失效测试完成，结果: Outbox 意图与数据库记录使用同一规范化 Key");
    }

    /**
     * 验证普通或敏感配置仍写数据库，但不创建平台公开缓存门禁。
     */
    @Test
    void shouldNotPrepareInvalidationForUnregisteredConfig() {
        log.info("测试未登记配置保存，关键输入: platform.gateway.api-secret");
        Fixture fixture = fixture();
        when(fixture.mapper().selectOne(any())).thenReturn(null);
        when(fixture.converter().toDTO(any())).thenReturn(new SysConfigDTO());

        fixture.service().saveConfig(request("platform.gateway.api-secret"));

        verify(fixture.coordinator(), never()).prepare(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.gateway.api-secret"
        );
        verify(fixture.mapper()).insert(any(SysConfigDO.class));
        log.info("未登记配置保存测试完成，结果: 数据库写入保留且未创建 Redis 失效事件");
    }

    /**
     * 验证删除公开配置时在数据库软删除前登记可靠失效意图。
     */
    @Test
    void shouldPrepareReliableInvalidationWhenDeletingPublicConfig() {
        log.info("测试公开配置删除失效，关键输入: platform.admin.frontend-base-url");
        Fixture fixture = fixture();
        SysConfigDO existing = new SysConfigDO();
        existing.setId(77L);
        existing.setConfigKey("platform.admin.frontend-base-url");
        existing.setDeleted(0L);
        when(fixture.mapper().selectOne(any())).thenReturn(existing);

        fixture.service().deleteConfig("platform.admin.frontend-base-url");

        verify(fixture.coordinator()).prepare(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.admin.frontend-base-url"
        );
        verify(fixture.mapper()).updateById(argThat(
                (SysConfigDO row) -> Long.valueOf(77L).equals(row.getDeleted())
        ));
        log.info("公开配置删除失效测试完成，结果: 已登记 Outbox 且配置完成软删除");
    }

    /**
     * 验证保存和删除入口都声明数据库事务，保证 Outbox 与业务数据同提交或同回滚。
     *
     * @throws NoSuchMethodException 方法签名变更时抛出
     */
    @Test
    void shouldKeepPublicConfigMutationsInsideDatabaseTransactions()
            throws NoSuchMethodException {
        log.info("测试公开配置事务契约，关键输入: saveConfig 与 deleteConfig");
        Method save = AdminConfigServiceImpl.class.getMethod(
                "saveConfig",
                SysConfigSaveRequest.class
        );
        Method delete = AdminConfigServiceImpl.class.getMethod("deleteConfig", String.class);

        assertRollbackForException(save.getAnnotation(Transactional.class));
        assertRollbackForException(delete.getAnnotation(Transactional.class));
        log.info("公开配置事务契约测试完成，结果: 两个写入口均按 Exception 回滚");
    }

    /**
     * 创建隔离的管理端配置服务测试夹具。
     *
     * @return 服务及其三个可验证依赖
     */
    private Fixture fixture() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        ConfigConverter converter = mock(ConfigConverter.class);
        MerchantSecurityCacheInvalidationCoordinator coordinator =
                mock(MerchantSecurityCacheInvalidationCoordinator.class);
        return new Fixture(
                new AdminConfigServiceImpl(mapper, converter, coordinator),
                mapper,
                converter,
                coordinator
        );
    }

    /**
     * 创建满足服务字段填充要求的配置保存请求。
     *
     * @param configKey 配置键
     * @return 测试保存请求
     */
    private SysConfigSaveRequest request(String configKey) {
        SysConfigSaveRequest request = new SysConfigSaveRequest();
        request.setConfigKey(configKey);
        request.setConfigName("测试配置");
        request.setConfigValue(configKey.contains("base-url")
                ? "https://example.com"
                : "secret-value");
        request.setValueType(1);
        request.setOperator("unit-test");
        return request;
    }

    /**
     * 断言事务声明包含 {@link Exception} 回滚策略。
     *
     * @param transactional 待验证的事务注解
     */
    private void assertRollbackForException(Transactional transactional) {
        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }

    /**
     * 管理端配置服务测试依赖集合。
     *
     * @param service     待测试服务
     * @param mapper      系统配置 Mapper
     * @param converter   配置对象转换器
     * @param coordinator 永久缓存失效协调器
     */
    private record Fixture(
            AdminConfigServiceImpl service,
            SysConfigMapper mapper,
            ConfigConverter converter,
            MerchantSecurityCacheInvalidationCoordinator coordinator) {
    }
}

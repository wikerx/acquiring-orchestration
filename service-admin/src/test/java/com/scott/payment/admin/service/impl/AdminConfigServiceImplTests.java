package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.converter.ConfigConverter;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.entity.SysConfigDO;
import com.scott.payment.admin.mapper.SysConfigMapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端系统参数统一永久缓存读写契约测试。
 */
@Slf4j
class AdminConfigServiceImplTests {

    /** 保存任意全局唯一配置键时都必须登记统一缓存失效意图。 */
    @Test
    void shouldPrepareReliableInvalidationWhenSavingAnySystemConfig() {
        Fixture fixture = fixture();
        when(fixture.mapper().selectOne(any())).thenReturn(null);
        when(fixture.converter().toDTO(any(SysConfigDO.class))).thenReturn(new SysConfigDTO());
        SysConfigSaveRequest request = request(" platform.gateway.api-secret ");

        fixture.service().saveConfig(request);

        verify(fixture.coordinator()).prepare(
                PaymentCacheNames.SYSTEM_CONFIG,
                "platform.gateway.api-secret"
        );
        verify(fixture.mapper()).insert(argThat((SysConfigDO row) ->
                "platform.gateway.api-secret".equals(row.getConfigKey())
        ));
        assertThat(request.getConfigKey()).isEqualTo("platform.gateway.api-secret");
    }

    /** 删除配置时必须在数据库软删除前登记同一个缓存 Key 的可靠失效。 */
    @Test
    void shouldPrepareReliableInvalidationWhenDeletingSystemConfig() {
        Fixture fixture = fixture();
        SysConfigDO existing = new SysConfigDO();
        existing.setId(77L);
        existing.setConfigKey("risk.transaction.max-attempts");
        existing.setDeleted(0L);
        when(fixture.mapper().selectOne(any())).thenReturn(existing);

        fixture.service().deleteConfig("risk.transaction.max-attempts");

        verify(fixture.coordinator()).prepare(
                PaymentCacheNames.SYSTEM_CONFIG,
                "risk.transaction.max-attempts"
        );
        verify(fixture.mapper()).updateById(argThat(
                (SysConfigDO row) -> Long.valueOf(77L).equals(row.getDeleted())
        ));
    }

    /** Admin 详情查询必须复用跨服务缓存快照而不是再次直接查询 Mapper。 */
    @Test
    void shouldReadAdminDetailFromSharedSystemConfigReader() {
        Fixture fixture = fixture();
        SystemConfigSnapshot snapshot = snapshot(
                "platform.gateway.base-url",
                "https://gateway.example.com",
                1
        );
        SysConfigDTO dto = new SysConfigDTO();
        dto.setConfigKey(snapshot.configKey());
        when(fixture.readService().findByKey("platform.gateway.base-url"))
                .thenReturn(java.util.Optional.of(snapshot));
        when(fixture.converter().toDTO(snapshot)).thenReturn(dto);

        SysConfigDTO result = fixture.service().getConfigByKey(" platform.gateway.base-url ");

        assertThat(result).isSameAs(dto);
        verify(fixture.readService()).findByKey("platform.gateway.base-url");
    }

    /** Admin 批量配置值读取必须逐键复用共享缓存并过滤停用或空值。 */
    @Test
    void shouldReadEnabledConfigValuesFromSharedCache() {
        Fixture fixture = fixture();
        when(fixture.readService().findEnabledValue("platform.gateway.base-url"))
                .thenReturn(java.util.Optional.of("https://gateway.example.com"));
        when(fixture.readService().findEnabledValue("risk.disabled"))
                .thenReturn(java.util.Optional.empty());

        Map<String, String> result = fixture.service().enabledConfigValues(Set.of(
                " platform.gateway.base-url ",
                "risk.disabled"
        ));

        assertThat(result).containsExactly(
                Map.entry("platform.gateway.base-url", "https://gateway.example.com")
        );
    }

    /** 保存和删除入口必须保留数据库事务，保证 Outbox 与业务数据同提交或同回滚。 */
    @Test
    void shouldKeepSystemConfigMutationsInsideDatabaseTransactions()
            throws NoSuchMethodException {
        Method save = AdminConfigServiceImpl.class.getMethod(
                "saveConfig",
                SysConfigSaveRequest.class
        );
        Method delete = AdminConfigServiceImpl.class.getMethod("deleteConfig", String.class);

        assertRollbackForException(save.getAnnotation(Transactional.class));
        assertRollbackForException(delete.getAnnotation(Transactional.class));
        assertThat(save.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
        assertThat(delete.getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
    }

    private Fixture fixture() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        ConfigConverter converter = mock(ConfigConverter.class);
        ManagedCacheInvalidationCoordinator coordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        SystemConfigReadService readService = mock(SystemConfigReadService.class);
        return new Fixture(
                new AdminConfigServiceImpl(mapper, converter, coordinator, readService),
                mapper,
                converter,
                coordinator,
                readService
        );
    }

    private SysConfigSaveRequest request(String configKey) {
        SysConfigSaveRequest request = new SysConfigSaveRequest();
        request.setConfigKey(configKey);
        request.setConfigName("测试配置");
        request.setConfigValue("test-value");
        request.setValueType(1);
        request.setOperator("unit-test");
        return request;
    }

    private SystemConfigSnapshot snapshot(String configKey, String value, int status) {
        LocalDateTime now = LocalDateTime.now();
        return new SystemConfigSnapshot(
                1L,
                "测试配置",
                configKey,
                value,
                1,
                "system",
                1,
                1,
                0,
                status,
                null,
                "system",
                "system",
                now,
                now
        );
    }

    private void assertRollbackForException(Transactional transactional) {
        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }

    private record Fixture(
            AdminConfigServiceImpl service,
            SysConfigMapper mapper,
            ConfigConverter converter,
            ManagedCacheInvalidationCoordinator coordinator,
            SystemConfigReadService readService) {
    }
}

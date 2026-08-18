package com.scott.payment.component.db.systemconfig.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.systemconfig.entity.SystemConfigDO;
import com.scott.payment.component.db.systemconfig.mapper.SystemConfigMapper;
import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 系统参数 Cacheable 代理和数据库快照映射测试。 */
class SystemConfigCacheReaderTests {

    /** 缓存入口必须使用统一 Cache Name、原始配置键并强制回源主库。 */
    @Test
    void shouldDeclarePersistentSystemConfigCacheContract() throws NoSuchMethodException {
        Method method = SystemConfigCacheReader.class.getMethod("findCached", String.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        DS dataSource = method.getAnnotation(DS.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.SYSTEM_CONFIG);
        assertThat(cacheable.key()).isEqualTo("#p0");
        assertThat(cacheable.unless()).isEqualTo("#result == null");
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }

    /** 数据库未删除记录必须完整转换为跨服务缓存快照。 */
    @Test
    void shouldMapDatabaseRowToSharedSnapshot() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        SystemConfigDO row = new SystemConfigDO();
        LocalDateTime now = LocalDateTime.now();
        row.setId(9L);
        row.setConfigName("系统名称");
        row.setConfigKey("system.name");
        row.setConfigValue("Vexra");
        row.setValueType(1);
        row.setConfigGroup("system");
        row.setSystemBuiltin(1);
        row.setVisible(1);
        row.setEncrypted(0);
        row.setStatus(1);
        row.setRemark("display name");
        row.setCreatedBy("system");
        row.setUpdatedBy("admin");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setDeleted(0L);
        when(mapper.selectOne(any())).thenReturn(row);

        SystemConfigSnapshot result = new SystemConfigCacheReader(mapper).findFresh("system.name");

        assertThat(result.configKey()).isEqualTo("system.name");
        assertThat(result.configValue()).isEqualTo("Vexra");
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.updatedAt()).isEqualTo(now);
    }
}

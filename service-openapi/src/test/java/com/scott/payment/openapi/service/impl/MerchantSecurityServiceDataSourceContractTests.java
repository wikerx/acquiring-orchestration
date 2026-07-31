package com.scott.payment.openapi.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户鉴权关键读取的数据源路由契约测试。
 */
class MerchantSecurityServiceDataSourceContractTests {

    @Test
    void shouldReadMerchantJwtKeyFromMaster() throws NoSuchMethodException {
        DS dataSource = MerchantSecurityServiceImpl.class
                .getMethod("getMerchantKey", String.class)
                .getAnnotation(DS.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }
}

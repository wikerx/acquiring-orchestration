package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentReferenceDataSourceContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 固定支付 BIN 缓存回源使用只读数据源，不占用交易主库连接。
 * @status : create
 */
class PaymentReferenceDataSourceContractTests {

    /**
     * 验证 BIN 前缀查询固定路由从库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteCardBinLookupToSlave() throws NoSuchMethodException {
        Method method = PaymentCardBinCacheReader.class.getMethod("findByPrefix", String.class);
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.SLAVE);
    }
}

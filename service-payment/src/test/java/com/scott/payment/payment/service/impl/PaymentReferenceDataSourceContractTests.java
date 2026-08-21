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
 * @description : 支付 BIN 缓存失效后的首次回源使用主库，避免复制延迟重新缓存旧区间。
 * @status : create
 */
class PaymentReferenceDataSourceContractTests {

    /**
     * 验证 BIN 前缀缓存重建固定路由主库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteCardBinCacheRebuildToMaster() throws NoSuchMethodException {
        Method method = PaymentCardBinCacheReader.class.getMethod("findByPrefix", String.class);
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }
}

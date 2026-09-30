package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.service.impl.ReferenceDataLookupServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentReferenceDataSourceContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 支付 BIN 缓存失效后的数据库回源使用独立只读从库事务。
 * @status : create
 */
class PaymentReferenceDataSourceContractTests {

    /**
     * 验证共享 BIN 缓存调用的基础数据查询路由从库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteCardBinCacheRebuildToSlave() throws NoSuchMethodException {
        Method method = ReferenceDataLookupServiceImpl.class.getMethod("lookupCardBinSnapshot", String.class);
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.SLAVE);
    }
}

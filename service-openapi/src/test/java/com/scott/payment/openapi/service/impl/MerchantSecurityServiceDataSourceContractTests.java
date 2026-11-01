package com.scott.payment.openapi.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户鉴权关键读取的数据源路由契约测试。
 */
class MerchantSecurityServiceDataSourceContractTests {

    @Test
    void shouldReadAllSecurityCriticalMaterialFromMaster() throws NoSuchMethodException {
        List<String> methodNames = List.of(
                "getMerchantKey",
                "getPlatformPrivateKey",
                "getPlatformPublicKey",
                "getActiveMerchant",
                "getMerchantResponsePublicKey",
                "getMerchantClientSecurityMaterial",
                "getServerSecurityMaterial"
        );

        for (String methodName : methodNames) {
            Method method = MerchantSecurityServiceImpl.class.getMethod(methodName, String.class);
            DS dataSource = method.getAnnotation(DS.class);
            assertThat(dataSource)
                    .as("method %s must declare its security-critical data source", methodName)
                    .isNotNull();
            assertThat(dataSource.value())
                    .as("method %s must bypass replica lag", methodName)
                    .isEqualTo(DataSourceName.MASTER);
        }
    }
}

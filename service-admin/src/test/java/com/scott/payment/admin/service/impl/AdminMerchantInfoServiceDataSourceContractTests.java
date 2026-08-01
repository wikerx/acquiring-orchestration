package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoServiceDataSourceContractTests
 * @date : 2026-08-01 13:00
 * @email : scott_x@163.com
 * @description : 验证管理端商户普通查询路由只读库，新增、修改、状态和密钥变更固定路由主库
 * @status : create
 */
class AdminMerchantInfoServiceDataSourceContractTests {

    /**
     * 验证普通管理查询固定使用 SLAVE，降低主库只读压力。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteMerchantQueriesToSlave() throws NoSuchMethodException {
        assertDataSource(DataSourceName.SLAVE, List.of(
                method("getFormOptions"),
                method("pageMerchants", AdminMerchantQueryRequest.class),
                method("getMerchant", Long.class),
                method("getMerchantKeys", String.class)
        ));
    }

    /**
     * 验证所有商户事实和密钥材料变更固定使用 MASTER。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteMerchantMutationsToMaster() throws NoSuchMethodException {
        assertDataSource(DataSourceName.MASTER, List.of(
                method("createMerchant", AdminMerchantSaveRequest.class),
                method("updateMerchant", Long.class, AdminMerchantSaveRequest.class),
                method("updateStatus", Long.class, Integer.class),
                method("deleteMerchant", Long.class),
                method("provisionSecurityMaterial", String.class),
                method("rotateJwtKey", String.class),
                method("rotatePlatformPayloadKey", String.class),
                method("rotateMerchantResponseKey", String.class),
                method("updateMerchantResponseKey", String.class, AdminMerchantResponseKeyRequest.class)
        ));
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return AdminMerchantInfoServiceImpl.class.getMethod(name, parameterTypes);
    }

    private void assertDataSource(String expected, List<Method> methods) {
        for (Method method : methods) {
            DS dataSource = method.getAnnotation(DS.class);
            assertThat(dataSource)
                    .as("method %s must declare a data source", method.getName())
                    .isNotNull();
            assertThat(dataSource.value())
                    .as("method %s data source", method.getName())
                    .isEqualTo(expected);
        }
    }
}

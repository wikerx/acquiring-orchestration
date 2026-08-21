package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationalReadDataSourceContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 固定商户端字典和操作日志普通查询使用只读数据源。
 * @status : create
 */
class MerchantOperationalReadDataSourceContractTests {

    /**
     * 验证商户端非强一致管理查询固定路由从库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteMerchantOperationalQueriesToSlave() throws NoSuchMethodException {
        assertDataSource(MerchantDictServiceImpl.class.getMethod("pageDictData", DictDataQuery.class));
        assertDataSource(MerchantOperLogServiceImpl.class.getMethod(
                "pageOperLogs", SysOperLogQueryRequest.class));
    }

    /**
     * 断言指定公开方法声明 SLAVE 路由。
     *
     * @param method 被验证的公开方法
     */
    private void assertDataSource(Method method) {
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource)
                .as("method %s must declare a data source", method.getName())
                .isNotNull();
        assertThat(dataSource.value())
                .as("method %s data source", method.getName())
                .isEqualTo(DataSourceName.SLAVE);
    }
}

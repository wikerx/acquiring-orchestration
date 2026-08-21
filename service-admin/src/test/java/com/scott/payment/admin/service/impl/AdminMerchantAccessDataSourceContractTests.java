package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantAccessDataSourceContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 固定商户访问配置的后台查询与内部安全查询数据源边界。
 * @status : create
 */
class AdminMerchantAccessDataSourceContractTests {

    /**
     * 验证管理端 IP 白名单分页、导出和详情查询使用从库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteManagementQueriesToSlave() throws NoSuchMethodException {
        assertDataSource(AdminMerchantIpWhitelistServiceImpl.class.getMethod(
                "pageWhitelists", MerchantIpWhitelistQuery.class), DataSourceName.SLAVE);
        assertDataSource(AdminMerchantIpWhitelistServiceImpl.class.getMethod(
                "listWhitelists", MerchantIpWhitelistQuery.class), DataSourceName.SLAVE);
        assertDataSource(AdminMerchantIpWhitelistServiceImpl.class.getMethod(
                "getWhitelist", Long.class), DataSourceName.SLAVE);
    }

    /**
     * 验证商户内部安全配置查询直接读取主库，避免复制延迟放大访问控制窗口。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteInternalSecurityQueriesToMaster() throws NoSuchMethodException {
        assertDataSource(AdminMerchantIpWhitelistServiceImpl.class.getMethod(
                "listMerchantWhitelists", String.class), DataSourceName.MASTER);
        assertDataSource(AdminRiskManagementApplicationService.class.getMethod(
                "listMerchantSourceUrls", String.class), DataSourceName.MASTER);
    }

    /**
     * 断言指定公开方法声明预期的数据源路由。
     *
     * @param method   被验证的公开方法
     * @param expected 预期数据源名称
     */
    private void assertDataSource(Method method, String expected) {
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource)
                .as("method %s must declare a data source", method.getName())
                .isNotNull();
        assertThat(dataSource.value())
                .as("method %s data source", method.getName())
                .isEqualTo(expected);
    }
}

package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateBatchSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.GenerateBusinessRateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateServiceDataSourceContractTests
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 验证汇率管理纯查询固定路由从库，汇率配置及状态变更固定路由主库并保持事务边界。
 * @status : create
 */
class AdminExchangeRateServiceDataSourceContractTests {

    /**
     * 验证汇率源、原始汇率、规则、业务汇率和使用快照查询固定使用 SLAVE。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteExchangeRateQueriesToSlave() throws NoSuchMethodException {
        assertDataSource(DataSourceName.SLAVE, List.of(
                method("pageSources", SourceQuery.class),
                method("listSources", SourceQuery.class),
                method("getSource", Long.class),
                method("pageRawRates", RawRateQuery.class),
                method("listRawRates", RawRateQuery.class),
                method("getRawRate", Long.class),
                method("pageRules", RuleQuery.class),
                method("listRules", RuleQuery.class),
                method("getRule", Long.class),
                method("pageBusinessRates", BusinessRateQuery.class),
                method("listBusinessRates", BusinessRateQuery.class),
                method("getBusinessRate", Long.class),
                method("pageUsageSnapshots", UsageSnapshotQuery.class),
                method("listUsageSnapshots", UsageSnapshotQuery.class),
                method("getUsageSnapshot", Long.class)
        ));
    }

    /**
     * 验证汇率配置和状态变更固定使用 MASTER，并保留数据库事务。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteExchangeRateMutationsToMaster() throws NoSuchMethodException {
        List<Method> methods = List.of(
                method("createSource", SourceSaveRequest.class),
                method("updateSource", Long.class, SourceSaveRequest.class),
                method("updateSourceStatus", Long.class, Integer.class),
                method("deleteSource", Long.class),
                method("createManualRawRate", RawRateSaveRequest.class),
                method("voidRawRate", Long.class, String.class),
                method("createRule", RuleSaveRequest.class),
                method("updateRule", Long.class, RuleSaveRequest.class),
                method("updateRuleStatus", Long.class, Integer.class),
                method("createManualBusinessRate", BusinessRateSaveRequest.class),
                method("createManualBusinessRates", BusinessRateBatchSaveRequest.class),
                method("generateBusinessRate", GenerateBusinessRateRequest.class),
                method("updateBusinessRateStatus", Long.class, Integer.class)
        );
        assertDataSource(DataSourceName.MASTER, methods);
        for (Method method : methods) {
            assertThat(method.getAnnotation(Transactional.class))
                    .as("method %s must keep a transaction boundary", method.getName())
                    .isNotNull();
        }
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return AdminExchangeRateServiceImpl.class.getMethod(name, parameterTypes);
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

package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceDataSourceContractTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户费率、资金账户和余额流水只读查询的数据源路由契约测试。
 * @status : create
 */
class MerchantFinanceDataSourceContractTests {

    /**
     * 验证商户财务服务不声明类级主库路由，普通只读接口固定使用 SLAVE。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteOrdinaryMerchantFinanceQueriesToSlave() throws NoSuchMethodException {
        System.out.println("数据源契约：验证商户账户和流水普通查询使用 SLAVE");
        assertThat(MerchantFinanceServiceImpl.class.getAnnotation(DS.class)).isNull();
        assertDataSource(DataSourceName.SLAVE, List.of(
                method("getFundAccount", String.class),
                method("pageLedgers", String.class, DetailQuery.class)
        ));
    }

    /**
     * 验证商户当前费率常驻缓存未命中时固定回源 MASTER，避免复制延迟缓存旧费率。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRoutePersistentActiveFeeCacheMissToMaster() throws NoSuchMethodException {
        System.out.println("数据源契约：验证商户当前费率缓存未命中使用 MASTER 权威回源");
        assertDataSource(DataSourceName.MASTER, List.of(
                method("getCurrentFee", String.class)
        ));
    }

    /**
     * 验证在途统计只使用 transaction 普通读和已配置从库，且不会继承调用方事务连接。
     *
     * @throws NoSuchMethodException 查询方法签名变化时由测试显式失败
     */
    @Test
    void shouldRoutePendingStatisticsToTransactionReplicas() throws NoSuchMethodException {
        Method method = JdbcMerchantPendingBalanceQueryService.class
                .getMethod("sumPendingBalances", String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(JdbcMerchantPendingBalanceQueryService.class.getAnnotation(DS.class)).isNull();
        assertThat(method.getAnnotation(DS.class)).isNull();
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(TransactionLogicalReadExecutor.class
                .getMethod("read", java.util.function.Supplier.class)
                .getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
        assertThat(new TransactionShardingProperties().getReplicaDataSources())
                .containsExactly(DataSourceName.SLAVE_1, DataSourceName.SLAVE_2);
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return MerchantFinanceServiceImpl.class.getMethod(name, parameterTypes);
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

package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFinancialDataSourceContractTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用、资金账户和结算日历的数据源路由契约测试，防止类级主库注解覆盖查询方法。
 * @status : create
 */
class AdminFinancialDataSourceContractTests {

    /** 验证财务相关服务禁止声明类级数据源，所有路由必须由具体方法表达。 */
    @Test
    void shouldNotDeclareClassLevelDataSource() {
        System.out.println("数据源契约：验证费用、资金账户和节假日日历服务没有类级 @DS");
        assertThat(List.of(AdminFeeServiceImpl.class, AdminFundAccountServiceImpl.class,
                        AdminHolidayCalendarServiceImpl.class, AdminSettlementRateResolver.class,
                        JdbcAdminTransactionFundQueryService.class))
                .allSatisfy(type -> assertThat(type.getAnnotation(DS.class)).isNull());
    }

    /**
     * 验证在途统计挂起外层主库事务，并只通过 transaction 普通读路由到已配置从库。
     *
     * @throws NoSuchMethodException 查询方法签名变化时由测试显式失败
     */
    @Test
    void shouldRoutePendingStatisticsToTransactionReplicas() throws NoSuchMethodException {
        Method sumMethod = method(JdbcAdminTransactionFundQueryService.class,
                "sumPendingBalances", String.class);
        Method activityMethod = method(JdbcAdminTransactionFundQueryService.class,
                "hasSuccessfulFundTransaction", String.class);
        assertThat(List.of(sumMethod, activityMethod)).allSatisfy(method -> {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).isNotNull();
            assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
            assertThat(method.getAnnotation(DS.class)).isNull();
        });
        assertThat(TransactionLogicalReadExecutor.class
                .getMethod("read", java.util.function.Supplier.class)
                .getAnnotation(DS.class).value()).isEqualTo(DataSourceName.TRANSACTION);
        assertThat(new TransactionShardingProperties().getReplicaDataSources())
                .containsExactly(DataSourceName.SLAVE_1, DataSourceName.SLAVE_2);
    }

    /**
     * 验证管理端纯查询固定路由 SLAVE，避免账户列表和明细查询占用主库。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteFinancialQueriesToSlave() throws NoSuchMethodException {
        System.out.println("数据源契约：验证费用、资金和日历只读查询使用 SLAVE");
        assertDataSource(DataSourceName.SLAVE, List.of(
                method(AdminFeeServiceImpl.class, "pageTemplates", FeePlanQuery.class),
                method(AdminFeeServiceImpl.class, "getTemplate", Long.class),
                method(AdminFeeServiceImpl.class, "pageMerchantFees", FeePlanQuery.class),
                method(AdminFeeServiceImpl.class, "getMerchantFee", String.class),
                method(AdminFeeServiceImpl.class, "pageReviews", FeePlanQuery.class),
                method(AdminFeeServiceImpl.class, "pageSimulationRecords", FeeSimulationRecordQuery.class),
                method(AdminFundAccountServiceImpl.class, "pageAccounts", FundAccountQuery.class),
                method(AdminFundAccountServiceImpl.class, "getAccount", Long.class),
                method(AdminFundAccountServiceImpl.class, "pageLedgers", Long.class, FundDetailQuery.class),
                method(AdminFundAccountServiceImpl.class, "pageAllLedgers", FundDetailQuery.class),
                method(AdminFundAccountServiceImpl.class, "pageRecharges", FundRechargeQuery.class),
                method(AdminHolidayCalendarServiceImpl.class, "getMonth", int.class, int.class),
                method(AdminHolidayCalendarServiceImpl.class, "isWorkingDay", LocalDate.class)
        ));
    }

    /**
     * 验证管理端配置、审批、账户状态和充值入账固定路由 MASTER 并保留事务边界。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteFinancialMutationsToMaster() throws NoSuchMethodException {
        System.out.println("数据源契约：验证费用、资金和日历写操作使用 MASTER 与事务");
        List<Method> methods = List.of(
                method(AdminFeeServiceImpl.class, "createTemplate", FeeTemplateCreateRequest.class,
                        Long.class, String.class),
                method(AdminFeeServiceImpl.class, "createTemplateVersion", Long.class,
                        FeeVersionSaveRequest.class, Long.class, String.class),
                method(AdminFeeServiceImpl.class, "createMerchantVersion", String.class,
                        MerchantFeeVersionSaveRequest.class, Long.class, String.class),
                method(AdminFeeServiceImpl.class, "approveVersion", Long.class, String.class,
                        Long.class, String.class),
                method(AdminFeeServiceImpl.class, "rejectVersion", Long.class, String.class,
                        Long.class, String.class),
                method(AdminFeeServiceImpl.class, "simulate", FeeSimulationRequest.class,
                        Long.class, String.class),
                method(AdminFundAccountServiceImpl.class, "createRecharge", FundRechargeCreateRequest.class,
                        Long.class, String.class, String.class),
                method(AdminFundAccountServiceImpl.class, "recheckRecharge", Long.class, String.class,
                        Long.class, String.class, String.class),
                method(AdminFundAccountServiceImpl.class, "changeAccountStatus", Long.class, Long.class,
                        String.class, String.class, Long.class, String.class),
                method(AdminHolidayCalendarServiceImpl.class, "initializeYear", int.class, String.class),
                method(AdminHolidayCalendarServiceImpl.class, "saveDays", CalendarBatchSaveRequest.class,
                        String.class),
                method(AdminHolidayCalendarServiceImpl.class, "confirmYear", int.class, String.class)
        );
        assertDataSource(DataSourceName.MASTER, methods);
        assertThat(methods).allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class))
                .as("method %s must keep a transaction boundary", method.getName())
                .isNotNull());
    }

    private Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getMethod(name, parameterTypes);
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

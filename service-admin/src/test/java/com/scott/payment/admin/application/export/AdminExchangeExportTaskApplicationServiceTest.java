package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeBusinessRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRawRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeUsageSnapshotSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeSourceSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeVolatilitySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertEventSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端汇率事实导出任务编排测试，验证三类查询和可信身份冻结。
 */
class AdminExchangeExportTaskApplicationServiceTest {

    /** 清理线程认证和语言上下文。 */
    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    /** 三类汇率任务必须冻结强类型条件，页面分页不得进入查询快照。 */
    @Test
    void shouldFreezeExchangeFactQueries() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(scopeResolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service =
                new AdminExportTaskApplicationService(client, scopeResolver);

        RawRateQuery rawQuery = new RawRateQuery();
        rawQuery.setSourceCode("BOC");
        rawQuery.setPageNo(7);
        rawQuery.setPageSize(100);
        ExchangeRawRateSubmitRequest rawRequest = new ExchangeRawRateSubmitRequest();
        rawRequest.setRequestId("exchange-raw-1");
        rawRequest.setQuery(rawQuery);
        service.submitExchangeRawRates(rawRequest);

        BusinessRateQuery businessQuery = new BusinessRateQuery();
        businessQuery.setRateType("TRANSACTION_RATE");
        businessQuery.setGenerateMethod("AUTO");
        ExchangeBusinessRateSubmitRequest businessRequest = new ExchangeBusinessRateSubmitRequest();
        businessRequest.setRequestId("exchange-business-1");
        businessRequest.setQuery(businessQuery);
        service.submitExchangeBusinessRates(businessRequest);

        UsageSnapshotQuery snapshotQuery = new UsageSnapshotQuery();
        snapshotQuery.setUsageScene("CLEARING");
        snapshotQuery.setBusinessNo("TXN-1001");
        ExchangeUsageSnapshotSubmitRequest snapshotRequest = new ExchangeUsageSnapshotSubmitRequest();
        snapshotRequest.setRequestId("exchange-snapshot-1");
        snapshotRequest.setQuery(snapshotQuery);
        service.submitExchangeUsageSnapshots(snapshotRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, times(3)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_EXCHANGE_RAW_RATE",
                "ADMIN_EXCHANGE_BUSINESS_RATE",
                "ADMIN_EXCHANGE_USAGE_SNAPSHOT");
        assertThat(commands.get(0).getTaskName()).isEqualTo("原始汇率导出");
        assertThat(commands.get(0).getExchangeRawRateQuery().getSourceCode()).isEqualTo("BOC");
        assertThat(commands.get(1).getExchangeBusinessRateQuery().getGenerateMethod()).isEqualTo("AUTO");
        assertThat(commands.get(2).getExchangeUsageSnapshotQuery().getBusinessNo()).isEqualTo("TXN-1001");
        assertThat(java.util.Arrays.stream(commands.get(0).getExchangeRawRateQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /** 五类配置与预警任务必须冻结强类型查询并使用稳定任务名称。 */
    @Test
    void shouldFreezeExchangeConfigurationAndAlertQueries() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(scopeResolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service =
                new AdminExportTaskApplicationService(client, scopeResolver);

        SourceQuery sourceQuery = new SourceQuery();
        sourceQuery.setKeyword("bank");
        ExchangeSourceSubmitRequest sourceRequest = new ExchangeSourceSubmitRequest();
        sourceRequest.setRequestId("exchange-source-1");
        sourceRequest.setQuery(sourceQuery);
        service.submitExchangeSources(sourceRequest);

        RuleQuery ruleQuery = new RuleQuery();
        ruleQuery.setRateType("TRANSACTION_RATE");
        ExchangeRuleSubmitRequest ruleRequest = new ExchangeRuleSubmitRequest();
        ruleRequest.setRequestId("exchange-rule-1");
        ruleRequest.setQuery(ruleQuery);
        service.submitExchangeRules(ruleRequest);

        VolatilityQuery volatilityQuery = new VolatilityQuery();
        volatilityQuery.setRateType("SETTLEMENT_RATE");
        volatilityQuery.setStartTime(LocalDateTime.of(2026, 11, 8, 0, 0));
        volatilityQuery.setEndTime(LocalDateTime.of(2026, 11, 11, 0, 0));
        ExchangeVolatilitySubmitRequest volatilityRequest = new ExchangeVolatilitySubmitRequest();
        volatilityRequest.setRequestId("exchange-volatility-1");
        volatilityRequest.setQuery(volatilityQuery);
        service.submitExchangeVolatility(volatilityRequest);

        AlertRuleQuery alertRuleQuery = new AlertRuleQuery();
        alertRuleQuery.setSystemMandatory(1);
        ExchangeAlertRuleSubmitRequest alertRuleRequest = new ExchangeAlertRuleSubmitRequest();
        alertRuleRequest.setRequestId("exchange-alert-rule-1");
        alertRuleRequest.setQuery(alertRuleQuery);
        service.submitExchangeAlertRules(alertRuleRequest);

        AlertEventQuery alertEventQuery = new AlertEventQuery();
        alertEventQuery.setEventStatus("OPEN");
        ExchangeAlertEventSubmitRequest alertEventRequest = new ExchangeAlertEventSubmitRequest();
        alertEventRequest.setRequestId("exchange-alert-event-1");
        alertEventRequest.setQuery(alertEventQuery);
        service.submitExchangeAlertEvents(alertEventRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, times(5)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_EXCHANGE_SOURCE", "ADMIN_EXCHANGE_RULE", "ADMIN_EXCHANGE_VOLATILITY",
                "ADMIN_EXCHANGE_ALERT_RULE", "ADMIN_EXCHANGE_ALERT_EVENT");
        assertThat(commands).extracting(CreateRequest::getTaskName).containsExactly(
                "汇率源导出", "汇率规则导出", "汇率波动导出", "汇率预警规则导出", "汇率预警事件导出");
        assertThat(commands.get(0).getExchangeSourceQuery().getKeyword()).isEqualTo("bank");
        assertThat(commands.get(1).getExchangeRuleQuery().getRateType()).isEqualTo("TRANSACTION_RATE");
        assertThat(commands.get(2).getExchangeVolatilityQuery().getRateType()).isEqualTo("SETTLEMENT_RATE");
        assertThat(commands.get(3).getExchangeAlertRuleQuery().getSystemMandatory()).isEqualTo(1);
        assertThat(commands.get(4).getExchangeAlertEventQuery().getEventStatus()).isEqualTo("OPEN");
    }

    /** 构造可信管理端操作人。 */
    private InternalAuthAccount adminAccount() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("exchange.export@example.test");
        account.setRealName("Exchange Export Operator");
        return account;
    }
}

package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.TradeBlackPaymentClientDTOs;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.dto.risk.TradeBlackWorkflowDTOs;
import com.scott.payment.admin.mapper.AdminTradeBlackWorkflowMapper;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminTradeBlackWorkflowApplicationServiceTest {

    @AfterEach
    void clearOperator() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldDeduplicateSameNormalizedValueAndMergeItsSources() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRiskManagementApplicationService riskService = mock(AdminRiskManagementApplicationService.class);
        AdminTradeBlackWorkflowApplicationService service = new AdminTradeBlackWorkflowApplicationService(
                paymentClient, riskService, mock(RiskManagementMapper.class),
                mock(AdminTradeBlackWorkflowMapper.class));
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setPermissions(List.of("*:*:*"));
        InternalAuthContextHolder.set(operator);

        TradeBlackPaymentClientDTOs.PersonAddress billing = new TradeBlackPaymentClientDTOs.PersonAddress();
        billing.setEmail("Same.User@example.com");
        TradeBlackPaymentClientDTOs.Payer payer = new TradeBlackPaymentClientDTOs.Payer();
        payer.setEmail("same.user@example.com");
        TradeBlackPaymentClientDTOs.FactItem fact = new TradeBlackPaymentClientDTOs.FactItem();
        fact.setFound(true);
        fact.setRowNo(1);
        fact.setInputOrderNo("TX-001");
        fact.setTransactionId("TX-001");
        fact.setBilling(billing);
        fact.setPayer(payer);
        TradeBlackPaymentClientDTOs.FactsResult facts = new TradeBlackPaymentClientDTOs.FactsResult();
        facts.setItems(List.of(fact));
        when(paymentClient.resolveTradeBlackTransactions(any())).thenReturn(facts);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("matchValueMasked", "s***@example.com");
        normalized.put("matchValueHash", "same-normalized-hash");
        when(riskService.prepareDerivedList(anyString(), anyString(), anyString(),
                any(RiskDTOs.RiskListSaveRequest.class), any(), any(), any(), anyString()))
                .thenReturn(new AdminRiskManagementApplicationService.DerivedListPlan(
                        RiskFunctionDefinition.require("BLACK", "email"), false, normalized, null));

        TradeBlackWorkflowDTOs.PreviewResponse response = service.preview(request("email"));

        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getSelectableCount()).isEqualTo(1);
        assertThat(response.getCandidates().get(0).getSourceLabel())
                .isEqualTo("账单邮箱、付款人邮箱");
    }

    @Test
    void shouldRejectReusedRequestIdWhenWorkflowPayloadChanged() {
        AdminTradeBlackWorkflowMapper workflowMapper = mock(AdminTradeBlackWorkflowMapper.class);
        when(workflowMapper.selectBatchByRequestId("request-001", 88L)).thenReturn(Map.of(
                "batch_no", "TB-001",
                "request_fingerprint", "different-fingerprint",
                "status", "SUCCESS"
        ));
        AdminTradeBlackWorkflowApplicationService service = new AdminTradeBlackWorkflowApplicationService(
                mock(PaymentInternalClient.class), mock(AdminRiskManagementApplicationService.class),
                mock(RiskManagementMapper.class), workflowMapper);
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setAccountId(88L);
        operator.setPermissions(List.of("*:*:*"));
        InternalAuthContextHolder.set(operator);
        TradeBlackWorkflowDTOs.ExecuteRequest executeRequest = new TradeBlackWorkflowDTOs.ExecuteRequest();
        executeRequest.setRequestId("request-001");
        executeRequest.setWorkflow(request("email"));
        executeRequest.setSelectedCandidateKeys(List.of("candidate-001"));

        assertThatThrownBy(() -> service.execute(executeRequest))
                .hasMessageContaining("请求幂等号已用于其他交易加黑操作");
    }

    @Test
    void shouldDeriveEmailUsernameAndDomainBeforeNormalization() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRiskManagementApplicationService riskService = mock(AdminRiskManagementApplicationService.class);
        AdminTradeBlackWorkflowApplicationService service = new AdminTradeBlackWorkflowApplicationService(
                paymentClient, riskService, mock(RiskManagementMapper.class),
                mock(AdminTradeBlackWorkflowMapper.class));
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setPermissions(List.of("*:*:*"));
        InternalAuthContextHolder.set(operator);

        TradeBlackPaymentClientDTOs.PersonAddress billing = new TradeBlackPaymentClientDTOs.PersonAddress();
        billing.setEmail("Same.User@Example.com");
        TradeBlackPaymentClientDTOs.FactItem fact = new TradeBlackPaymentClientDTOs.FactItem();
        fact.setFound(true);
        fact.setRowNo(1);
        fact.setInputOrderNo("TX-001");
        fact.setTransactionId("TX-001");
        fact.setBilling(billing);
        TradeBlackPaymentClientDTOs.FactsResult facts = new TradeBlackPaymentClientDTOs.FactsResult();
        facts.setItems(List.of(fact));
        when(paymentClient.resolveTradeBlackTransactions(any())).thenReturn(facts);

        when(riskService.prepareDerivedList(anyString(), anyString(), anyString(),
                any(RiskDTOs.RiskListSaveRequest.class), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String functionCode = invocation.getArgument(0);
                    RiskDTOs.RiskListSaveRequest saveRequest = invocation.getArgument(3);
                    if ("emailUsername".equals(functionCode)) {
                        assertThat(saveRequest.getMatchValuePlain()).isEqualTo("Same.User");
                    } else if ("emailDomain".equals(functionCode)) {
                        assertThat(saveRequest.getMatchValuePlain()).isEqualTo("Example.com");
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("matchValueMasked", functionCode);
                    data.put("matchValueHash", functionCode + "-hash");
                    return new AdminRiskManagementApplicationService.DerivedListPlan(
                            RiskFunctionDefinition.require("BLACK", functionCode), false, data, null);
                });

        TradeBlackWorkflowDTOs.PreviewRequest request = request("emailUsername");
        request.setFunctionCodes(List.of("emailUsername", "emailDomain"));
        TradeBlackWorkflowDTOs.PreviewResponse response = service.preview(request);

        assertThat(response.getCandidates()).hasSize(2);
        assertThat(response.getSelectableCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectMaskedCardNumberWhenFullPanIsUnavailable() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRiskManagementApplicationService riskService = mock(AdminRiskManagementApplicationService.class);
        AdminTradeBlackWorkflowApplicationService service = new AdminTradeBlackWorkflowApplicationService(
                paymentClient, riskService, mock(RiskManagementMapper.class),
                mock(AdminTradeBlackWorkflowMapper.class));
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setPermissions(List.of("*:*:*"));
        InternalAuthContextHolder.set(operator);

        TradeBlackPaymentClientDTOs.FactItem fact = new TradeBlackPaymentClientDTOs.FactItem();
        fact.setFound(true);
        fact.setRowNo(1);
        fact.setInputOrderNo("TX-001");
        fact.setTransactionId("TX-001");
        fact.setCardNumberMasked("344676******3887");
        fact.setPaymentAccountHash("trusted-card-hash");
        fact.setPaymentBrand("AMEX");
        TradeBlackPaymentClientDTOs.FactsResult facts = new TradeBlackPaymentClientDTOs.FactsResult();
        facts.setItems(List.of(fact));
        when(paymentClient.resolveTradeBlackTransactions(any())).thenReturn(facts);

        TradeBlackWorkflowDTOs.PreviewResponse response = service.preview(request("cardNo"));

        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getSelectableCount()).isZero();
        assertThat(response.getUnavailableCount()).isEqualTo(1);
        assertThat(response.getCandidates().get(0).getState()).isEqualTo("NOT_AVAILABLE");
        assertThat(response.getCandidates().get(0).getStateMessage())
                .contains("无法取得完整卡号");
        verifyNoInteractions(riskService);
    }

    @Test
    void shouldRenderRegionWithoutEmptyHierarchySeparators() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("countryAlpha3", "USA");
        data.put("stateProvinceName", "");
        data.put("cityName", null);

        AdminRiskManagementApplicationService.DerivedListPlan plan =
                new AdminRiskManagementApplicationService.DerivedListPlan(
                        RiskFunctionDefinition.require("BLACK", "region"), true, data, null);

        assertThat(plan.maskedValue()).isEqualTo("USA");
    }

    private TradeBlackWorkflowDTOs.PreviewRequest request(String functionCode) {
        TradeBlackWorkflowDTOs.OrderReference order = new TradeBlackWorkflowDTOs.OrderReference();
        order.setRowNo(1);
        order.setIdentifierType("SYSTEM_ORDER_NO");
        order.setOrderNo("TX-001");
        TradeBlackWorkflowDTOs.PreviewRequest request = new TradeBlackWorkflowDTOs.PreviewRequest();
        request.setInputMode("SINGLE");
        request.setScopeType("GLOBAL");
        request.setActionType("ADD");
        request.setFunctionCodes(List.of(functionCode));
        request.setOrders(List.of(order));
        return request;
    }
}

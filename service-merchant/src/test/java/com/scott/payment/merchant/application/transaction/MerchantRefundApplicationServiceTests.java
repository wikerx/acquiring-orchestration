package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import com.scott.payment.merchant.client.payment.PaymentInternalClient;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRefundApplicationServiceTests
 * @date : 2026-08-06 00:00
 * @description : 商户退款管理隔离测试，验证浏览器商户号被认证上下文商户号覆盖且详情查询携带租户边界。
 * @status : create
 */
class MerchantRefundApplicationServiceTests {

    @Test
    void searchShouldOverrideBrowserMerchantIdAndApplyMerchantMessage() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        MerchantRefundApplicationService service = service(paymentClient);
        RefundQuery query = new RefundQuery();
        query.setMerchantId("forged-merchant");
        RefundRecord record = new RefundRecord();
        record.setApprovalStatus("PENDING");
        RefundSearchResponse response = new RefundSearchResponse();
        response.setPage(PageResult.of(1, 1, 20, List.of(record)));
        when(paymentClient.searchRefunds(query)).thenReturn(response);

        RefundSearchResponse result = service.search("authenticated-merchant", query);

        ArgumentCaptor<RefundQuery> captor = ArgumentCaptor.forClass(RefundQuery.class);
        verify(paymentClient).searchRefunds(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("authenticated-merchant");
        assertThat(result.getPage().getRecords().get(0).getMerchantVisibleMessage())
                .isEqualTo("退款申请待平台处理");
    }

    @Test
    void detailShouldPassAuthenticatedMerchantIdToPayment() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        MerchantRefundApplicationService service = service(paymentClient);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 6, 15, 30);
        when(paymentClient.refundDetail("RT-1", transactionTime, "authenticated-merchant"))
                .thenReturn(new RefundDetailResponse());

        service.detail("authenticated-merchant", "RT-1", transactionTime);

        verify(paymentClient).refundDetail("RT-1", transactionTime, "authenticated-merchant");
    }

    private MerchantRefundApplicationService service(PaymentInternalClient paymentClient) {
        return new MerchantRefundApplicationService(
                paymentClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }
}

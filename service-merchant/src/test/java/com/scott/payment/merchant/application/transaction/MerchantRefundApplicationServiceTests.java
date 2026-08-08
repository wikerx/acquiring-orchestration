package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import com.scott.payment.merchant.service.MerchantRefundQueryService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
class MerchantRefundApplicationServiceTests {

    /** 商户退款分页必须覆盖浏览器商户号后调用本地查询服务。 */
    @Test
    void searchShouldOverrideBrowserMerchantIdAndApplyMerchantMessage() {
        log.info("用例：验证商户退款分页覆盖伪造商户号并调用service-merchant本地查询服务");
        MerchantRefundQueryService queryService = mock(MerchantRefundQueryService.class);
        MerchantRefundApplicationService service = service(queryService);
        RefundQuery query = new RefundQuery();
        query.setMerchantId("forged-merchant");
        RefundRecord record = new RefundRecord();
        record.setApprovalStatus("PENDING");
        RefundSearchResponse response = new RefundSearchResponse();
        response.setPage(PageResult.of(1, 1, 20, List.of(record)));
        when(queryService.search(query)).thenReturn(response);

        RefundSearchResponse result = service.search("authenticated-merchant", query);

        ArgumentCaptor<RefundQuery> captor = ArgumentCaptor.forClass(RefundQuery.class);
        verify(queryService).search(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("authenticated-merchant");
        assertThat(result.getPage().getRecords().get(0).getMerchantVisibleMessage())
                .isEqualTo("退款申请待平台处理");
        log.info("结果：认证商户号覆盖成功，退款记录返回统一商户可见说明");
    }

    /** 商户退款详情必须把认证商户号传给本地查询服务。 */
    @Test
    void detailShouldPassAuthenticatedMerchantIdToLocalQueryService() {
        log.info("用例：验证商户退款详情携带认证商户号调用本地查询服务");
        MerchantRefundQueryService queryService = mock(MerchantRefundQueryService.class);
        MerchantRefundApplicationService service = service(queryService);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 6, 15, 30);
        when(queryService.detail("authenticated-merchant", "RT-1", transactionTime))
                .thenReturn(new RefundDetailResponse());

        service.detail("authenticated-merchant", "RT-1", transactionTime);

        verify(queryService).detail("authenticated-merchant", "RT-1", transactionTime);
        log.info("结果：详情查询仅使用认证商户号作为数据边界");
    }

    private MerchantRefundApplicationService service(MerchantRefundQueryService queryService) {
        return new MerchantRefundApplicationService(
                queryService,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }
}

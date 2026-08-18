package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSearchResponse;
import com.scott.payment.admin.service.AdminRefundQueryService;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRefundApplicationServiceTests
 * @date : 2026-08-08 00:10
 * @email : scott_x@163.com
 * @description : 管理端退款应用边界测试，验证只读请求由 service-admin 本地查询服务完成且不再调用 Payment 查询接口。
 * @status : create
 */
@Slf4j
class AdminRefundApplicationServiceTests {

    /** 管理端退款分页必须调用本地查询服务。 */
    @Test
    void searchShouldUseLocalQueryService() {
        log.info("用例：验证管理端退款分页由service-admin本地查询服务执行");
        AdminRefundQueryService queryService = mock(AdminRefundQueryService.class);
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRefundApplicationService service = service(queryService, paymentClient);
        RefundQuery query = new RefundQuery();
        RefundSearchResponse expected = new RefundSearchResponse();
        when(queryService.search(query)).thenReturn(expected);

        assertThat(service.search(query)).isSameAs(expected);

        verify(queryService).search(query);
        verifyNoInteractions(paymentClient);
        log.info("结果：退款分页仅访问本地查询服务，Payment客户端未被调用");
    }

    /** 管理端退款详情必须调用本地查询服务。 */
    @Test
    void detailShouldUseLocalQueryService() {
        log.info("用例：验证管理端退款详情由service-admin本地查询服务执行");
        AdminRefundQueryService queryService = mock(AdminRefundQueryService.class);
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRefundApplicationService service = service(queryService, paymentClient);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 8, 9, 30);
        RefundDetailResponse expected = new RefundDetailResponse();
        when(queryService.detail("refund-1", transactionTime)).thenReturn(expected);

        assertThat(service.detail("refund-1", transactionTime)).isSameAs(expected);

        verify(queryService).detail("refund-1", transactionTime);
        verifyNoInteractions(paymentClient);
        log.info("结果：退款详情仅访问本地查询服务，Payment客户端未被调用");
    }

    private AdminRefundApplicationService service(AdminRefundQueryService queryService,
                                                  PaymentInternalClient paymentClient) {
        return new AdminRefundApplicationService(
                queryService,
                paymentClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }
}

package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.admin.service.AdminChannelMatchAbnormalQueryService;
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
 * @classname : AdminChannelMatchAbnormalApplicationServiceTests
 * @date : 2026-08-08 00:10
 * @email : scott_x@163.com
 * @description : 管理端勾兑异常应用边界测试，验证案件查询由 service-admin 完成且处置客户端不参与只读请求。
 * @status : create
 */
@Slf4j
class AdminChannelMatchAbnormalApplicationServiceTests {

    /** 勾兑异常分页必须调用本地查询服务。 */
    @Test
    void searchShouldUseLocalQueryService() {
        log.info("用例：验证勾兑异常分页由service-admin本地查询服务执行");
        AdminChannelMatchAbnormalQueryService queryService = mock(AdminChannelMatchAbnormalQueryService.class);
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminChannelMatchAbnormalApplicationService service = service(queryService, paymentClient);
        AbnormalQuery query = new AbnormalQuery();
        AbnormalSearchResponse expected = new AbnormalSearchResponse();
        when(queryService.search(query)).thenReturn(expected);

        assertThat(service.search(query)).isSameAs(expected);

        verify(queryService).search(query);
        verifyNoInteractions(paymentClient);
        log.info("结果：案件分页仅访问本地查询服务，Payment客户端未被调用");
    }

    /** 勾兑异常详情必须调用本地查询服务。 */
    @Test
    void detailShouldUseLocalQueryService() {
        log.info("用例：验证勾兑异常详情由service-admin本地查询服务执行");
        AdminChannelMatchAbnormalQueryService queryService = mock(AdminChannelMatchAbnormalQueryService.class);
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminChannelMatchAbnormalApplicationService service = service(queryService, paymentClient);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 8, 9, 45);
        AbnormalDetailResponse expected = new AbnormalDetailResponse();
        when(queryService.detail("event-1", transactionTime)).thenReturn(expected);

        assertThat(service.detail("event-1", transactionTime)).isSameAs(expected);

        verify(queryService).detail("event-1", transactionTime);
        verifyNoInteractions(paymentClient);
        log.info("结果：案件详情仅访问本地查询服务，Payment客户端未被调用");
    }

    private AdminChannelMatchAbnormalApplicationService service(
            AdminChannelMatchAbnormalQueryService queryService,
            PaymentInternalClient paymentClient) {
        return new AdminChannelMatchAbnormalApplicationService(
                queryService,
                paymentClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }
}

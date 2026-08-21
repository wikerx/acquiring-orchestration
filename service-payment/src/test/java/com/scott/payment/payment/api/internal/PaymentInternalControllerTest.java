package com.scott.payment.payment.api.internal;

import com.scott.payment.payment.application.PaymentCheckoutApplicationService;
import com.scott.payment.payment.application.PaymentTransactionApplicationService;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalControllerTest
 * @date : 2026-08-21 10:10
 * @email : scott_x@163.com
 * @description : 校验支付内部接口的 HTTP 参数绑定契约，避免编译参数名缺失导致定时任务无法进入业务处理
 * @status : create
 */
@Slf4j
class PaymentInternalControllerTest {

    @Test
    void shouldBindSingleChannelMatchTransactionIdAndShardTime() throws Exception {
        PaymentTransactionApplicationService transactionApplicationService =
                mock(PaymentTransactionApplicationService.class);
        PaymentCheckoutApplicationService checkoutApplicationService =
                mock(PaymentCheckoutApplicationService.class);
        TransactionChannelMatchResultDTO result = new TransactionChannelMatchResultDTO();
        result.setScannedCount(1);
        result.setMatchedCount(1);
        when(transactionApplicationService.requeryChannelMatch(eq("TX202608210001"), any()))
                .thenReturn(result);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentInternalController(
                transactionApplicationService, checkoutApplicationService)).build();

        mockMvc.perform(post("/internal/payment/channel-match/TX202608210001/requery")
                        .contentType("application/json")
                        .content("{\"transactionDateTime\":\"2026-08-21 10:11:12.123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scannedCount").value(1))
                .andExpect(jsonPath("$.data.matchedCount").value(1));

        verify(transactionApplicationService).requeryChannelMatch(eq("TX202608210001"), any());
    }

    /**
     * 校验超时关单接口能够从查询参数读取扫描上限并返回处理数量。
     *
     * @throws Exception Spring MVC 请求执行异常
     */
    @Test
    void shouldBindTimeoutCloseLimitFromQueryParameter() throws Exception {
        log.info("用例开始：校验超时关单接口显式绑定 limit 查询参数");
        PaymentTransactionApplicationService transactionApplicationService =
                mock(PaymentTransactionApplicationService.class);
        PaymentCheckoutApplicationService checkoutApplicationService =
                mock(PaymentCheckoutApplicationService.class);
        when(checkoutApplicationService.expireDue(any(LocalDateTime.class), eq(120))).thenReturn(3);
        PaymentInternalController controller = new PaymentInternalController(
                transactionApplicationService, checkoutApplicationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/internal/payment/checkout/session/expire-due")
                        .param("limit", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
        log.info("用例结果：limit=120 已绑定并返回超时关单处理数量 3");
    }
}

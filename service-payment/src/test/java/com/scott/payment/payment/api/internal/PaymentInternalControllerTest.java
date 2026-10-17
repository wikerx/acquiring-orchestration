package com.scott.payment.payment.api.internal;

import com.scott.payment.payment.application.PaymentCheckoutApplicationService;
import com.scott.payment.payment.application.PaymentTransactionApplicationService;
import com.scott.payment.payment.application.TransactionCallbackInboxApplicationService;
import com.scott.payment.payment.api.internal.dto.TransactionCallbackInboxAcceptResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
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

    /** 回调 HTTP 入口只能进入 Inbox 受理路径，旧同步业务处理路径不再暴露。 */
    @Test
    void shouldExposeOnlyCallbackInboxAcceptanceEndpoint() throws Exception {
        PaymentTransactionApplicationService transactionApplicationService =
                mock(PaymentTransactionApplicationService.class);
        PaymentCheckoutApplicationService checkoutApplicationService =
                mock(PaymentCheckoutApplicationService.class);
        TransactionCallbackInboxApplicationService callbackInboxApplicationService =
                mock(TransactionCallbackInboxApplicationService.class);
        TransactionCallbackInboxAcceptResultDTO result = new TransactionCallbackInboxAcceptResultDTO();
        result.setInboxId("CBI202610171200000000001");
        result.setInboxStatus("RECEIVED");
        result.setAcceptResult("ACCEPTED");
        when(callbackInboxApplicationService.accept(any(TransactionChannelCallbackCommandDTO.class)))
                .thenReturn(result);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentInternalController(
                transactionApplicationService, checkoutApplicationService,
                callbackInboxApplicationService)).build();
        String content = """
                {
                  "channelCode":"MPGS",
                  "callbackEventId":"event-001",
                  "requestBodyLength":128,
                  "requestBodySha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                  "signatureValid":true,
                  "ipAllowed":true
                }
                """;

        mockMvc.perform(post("/internal/payment/channel-callback/inbox")
                        .contentType("application/json")
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inboxId").value("CBI202610171200000000001"))
                .andExpect(jsonPath("$.data.acceptResult").value("ACCEPTED"));
        mockMvc.perform(post("/internal/payment/channel-callback")
                        .contentType("application/json")
                        .content(content))
                .andExpect(status().isNotFound());

        verify(callbackInboxApplicationService).accept(any(TransactionChannelCallbackCommandDTO.class));
    }

    @Test
    void shouldBindSingleChannelMatchTransactionIdAndShardTime() throws Exception {
        PaymentTransactionApplicationService transactionApplicationService =
                mock(PaymentTransactionApplicationService.class);
        PaymentCheckoutApplicationService checkoutApplicationService =
                mock(PaymentCheckoutApplicationService.class);
        TransactionCallbackInboxApplicationService callbackInboxApplicationService =
                mock(TransactionCallbackInboxApplicationService.class);
        TransactionChannelMatchResultDTO result = new TransactionChannelMatchResultDTO();
        result.setScannedCount(1);
        result.setMatchedCount(1);
        when(transactionApplicationService.requeryChannelMatch(eq("TX202608210001"), any()))
                .thenReturn(result);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentInternalController(
                transactionApplicationService, checkoutApplicationService,
                callbackInboxApplicationService)).build();

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
        TransactionCallbackInboxApplicationService callbackInboxApplicationService =
                mock(TransactionCallbackInboxApplicationService.class);
        when(checkoutApplicationService.expireDue(any(LocalDateTime.class), eq(120))).thenReturn(3);
        PaymentInternalController controller = new PaymentInternalController(
                transactionApplicationService, checkoutApplicationService,
                callbackInboxApplicationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/internal/payment/checkout/session/expire-due")
                        .param("limit", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
        log.info("用例结果：limit=120 已绑定并返回超时关单处理数量 3");
    }
}

package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.application.PaymentCheckoutApplicationService;
import com.scott.payment.payment.application.PaymentTransactionApplicationService;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchRequeryCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalController
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : 支付内部控制器，负责收银台、交易命令、渠道回调、渠道勾兑与商户交互审计回写，不承载管理端或商户端列表统计查询。
 * @status : create
 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    /**
     * 收单支付交易应用服务。
     */
    private final PaymentTransactionApplicationService paymentTransactionApplicationService;

    /**
     * Hosted Checkout 应用服务。
     */
    private final PaymentCheckoutApplicationService paymentCheckoutApplicationService;

    /**
     * 创建内部交易接口控制器。
     *
     * @param paymentTransactionApplicationService 收单交易应用服务
     * @param paymentCheckoutApplicationService Hosted Checkout 应用服务
     */
    public PaymentInternalController(PaymentTransactionApplicationService paymentTransactionApplicationService,
                                     PaymentCheckoutApplicationService paymentCheckoutApplicationService) {
        this.paymentTransactionApplicationService = paymentTransactionApplicationService;
        this.paymentCheckoutApplicationService = paymentCheckoutApplicationService;
    }

    /**
     * 创建 Hosted Checkout 会话。
     *
     * @param commandDTO 创建收银台会话命令
     * @return 收银台会话创建结果
     */
    @PostMapping("/checkout/session")
    public CommonResult<PaymentCheckoutSessionCreateResultDTO> createCheckoutSession(
            @Valid @RequestBody PaymentCheckoutSessionCreateCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.createSession(commandDTO));
    }

    /**
     * 查询 Hosted Checkout 会话展示状态。
     *
     * @param commandDTO 查询收银台命令
     * @return 收银台展示数据
     */
    @PostMapping("/checkout/session/query")
    public CommonResult<PaymentCheckoutSessionQueryResultDTO> queryCheckoutSession(
            @Valid @RequestBody PaymentCheckoutSessionQueryCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.querySession(commandDTO));
    }

    /**
     * 执行收银台付款期限补偿扫描，处理中交易由应用服务保持原状态。
     *
     * @param limit 单次扫描上限，未传时默认处理 200 条
     * @return 本次关闭的超时收银台订单数量
     */
    @PostMapping("/checkout/session/expire-due")
    public CommonResult<Integer> expireDueCheckoutSessions(
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return success(paymentCheckoutApplicationService.expireDue(LocalDateTime.now(), limit));
    }

    /** 解析卡 BIN 品牌并校验当前收银台会话是否支持。 */
    @PostMapping("/checkout/card-bin/resolve")
    public CommonResult<PaymentCheckoutCardBinResultDTO> resolveCheckoutCardBin(
            @Valid @RequestBody PaymentCheckoutCardBinCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.resolveCardBin(commandDTO));
    }

    /**
     * 提交 Hosted Checkout 银行卡支付。
     *
     * @param commandDTO 支付提交命令
     * @return 支付提交结果
     */
    @PostMapping("/checkout/payment/submit")
    public CommonResult<PaymentCheckoutPaymentResultDTO> submitCheckoutPayment(
            @Valid @RequestBody PaymentCheckoutPaymentSubmitCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.submitPayment(commandDTO));
    }

    /**
     * 查询 Hosted Checkout 支付处理状态。
     *
     * @param commandDTO 支付状态查询命令
     * @return 支付状态结果
     */
    @PostMapping("/checkout/payment/status")
    public CommonResult<PaymentCheckoutPaymentResultDTO> queryCheckoutPaymentStatus(
            @Valid @RequestBody PaymentCheckoutPaymentStatusCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.queryPaymentStatus(commandDTO));
    }

    /**
     * 处理 Hosted Checkout 3DS 浏览器回跳。
     *
     * @param commandDTO 3DS 回跳命令
     * @return 支付状态结果
     */
    @PostMapping("/checkout/3ds/return")
    public CommonResult<PaymentCheckoutPaymentResultDTO> handleCheckoutThreeDsReturn(
            @Valid @RequestBody PaymentCheckoutThreeDsReturnCommandDTO commandDTO) {
        return success(paymentCheckoutApplicationService.handleThreeDsReturn(commandDTO));
    }

    /**
     * 创建一步支付交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @PostMapping("/payment")
    public CommonResult<PaymentCreateResultDTO> createPayment(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.createPayment(commandDTO));
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @PostMapping("/authorization")
    public CommonResult<PaymentCreateResultDTO> createAuthorization(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.createAuthorization(commandDTO));
    }

    /**
     * 创建预授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @PostMapping("/pre-authorization")
    public CommonResult<PaymentCreateResultDTO> createPreAuthorization(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.createPreAuthorization(commandDTO));
    }

    /**
     * 创建增量授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @PostMapping("/incremental-authorization")
    public CommonResult<PaymentCreateResultDTO> createIncrementalAuthorization(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.createIncrementalAuthorization(commandDTO));
    }

    /**
     * 发起请款交易。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    @PostMapping("/capture")
    public CommonResult<PaymentCreateResultDTO> capture(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.capture(commandDTO));
    }

    /**
     * 发起预授权完成交易。
     *
     * @param commandDTO 预授权完成命令
     * @return 预授权完成结果
     */
    @PostMapping("/pre-auth-completion")
    public CommonResult<PaymentCreateResultDTO> preAuthCompletion(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.preAuthCompletion(commandDTO));
    }

    /**
     * 发起退款交易。
     *
     * @param commandDTO 退款命令
     * @return 退款结果
     */
    @PostMapping("/refund")
    public CommonResult<PaymentCreateResultDTO> refund(@Valid @RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.refund(commandDTO));
    }

    /**
     * 发起撤销交易。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    @PostMapping("/void")
    public CommonResult<PaymentCreateResultDTO> voidPayment(@RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.voidPayment(commandDTO));
    }

    /**
     * 查询交易状态。
     *
     * @param commandDTO 查询命令
     * @return 查询结果
     */
    @PostMapping("/query")
    public CommonResult<PaymentQueryResultDTO> query(@RequestBody PaymentCreateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.query(commandDTO));
    }

    /**
     * 记录渠道回调原文和业务幂等结果。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 渠道回调记录结果
     */
    @PostMapping("/channel-callback")
    public CommonResult<TransactionChannelCallbackResultDTO> recordChannelCallback(
            @Valid @RequestBody TransactionChannelCallbackCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.recordChannelCallback(commandDTO));
    }

    /**
     * 执行渠道交易查询勾兑。
     *
     * @param commandDTO 查询勾兑命令
     * @return 查询勾兑处理结果
     */
    @PostMapping("/transactions/channel-match/match-due")
    public CommonResult<TransactionChannelMatchResultDTO> matchDueChannelTransactions(
            @RequestBody TransactionChannelMatchCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.matchDueChannelTransactions(commandDTO));
    }

    /**
     * 主动重查并勾兑单笔交易。
     *
     * @param transactionId 平台交易号
     * @param commandDTO 真实交易分片时间
     * @return 单笔勾兑处理结果
     */
    @PostMapping("/channel-match/{transactionId}/requery")
    public CommonResult<TransactionChannelMatchResultDTO> requeryChannelMatch(
            @PathVariable("transactionId") String transactionId,
            @Valid @RequestBody TransactionChannelMatchRequeryCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.requeryChannelMatch(transactionId, commandDTO));
    }

    /**
     * 回写商户 OpenAPI 响应密文摘要。
     * <p>
     * 该接口只允许 service-openapi 在响应 data 加密完成后调用，用于补齐交易详情中的商户交互日志。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志
     */
    @PostMapping("/transactions/merchant-api-logs/response")
    public CommonResult<Boolean> updateMerchantApiResponseLog(
            @RequestBody TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.updateMerchantApiResponseLog(commandDTO));
    }
}

package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.payment.application.PaymentTransactionApplicationService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelCallbackQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelLogQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.MerchantNotificationQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionDetailResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSearchResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPageQuery;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;


@RestController
@RequestMapping("/internal/payment")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalController
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : PaymentInternalController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PaymentInternalController {

    /**
     * 收单支付交易应用服务。
     */
    private final PaymentTransactionApplicationService paymentTransactionApplicationService;

    /**
     * 创建内部交易接口控制器。
     *
     * @param paymentTransactionApplicationService 收单交易应用服务
     */
    public PaymentInternalController(PaymentTransactionApplicationService paymentTransactionApplicationService) {
        this.paymentTransactionApplicationService = paymentTransactionApplicationService;
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
     * 分页查询交易生命周期主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    @PostMapping("/transactions/orders/search")
    public CommonResult<PageResult<TransactionOrderResponse>> pageOrders(@RequestBody(required = false) TransactionPageQuery query) {
        return success(paymentTransactionApplicationService.pageOrders(query));
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    @PostMapping("/transactions/operations/search")
    public CommonResult<PageResult<TransactionOperationResponse>> pageOperations(@RequestBody(required = false) TransactionPageQuery query) {
        return success(paymentTransactionApplicationService.pageOperations(query));
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页和统计结果
     */
    @PostMapping("/transactions/operations/search-with-summary")
    public CommonResult<TransactionOperationSearchResponse> searchOperations(@RequestBody(required = false) TransactionPageQuery query) {
        return success(paymentTransactionApplicationService.searchOperations(query));
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易详情
     */
    @GetMapping("/transactions/{transactionId}")
    public CommonResult<TransactionDetailResponse> detail(@PathVariable("transactionId") String transactionId) {
        return success(paymentTransactionApplicationService.detail(transactionId));
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道日志分页结果
     */
    @PostMapping("/transactions/channel-logs/search")
    public CommonResult<PageResult<?>> pageChannelLogs(@RequestBody(required = false) ChannelLogQuery query) {
        return success(paymentTransactionApplicationService.pageChannelLogs(query));
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调业务记录分页结果
     */
    @PostMapping("/transactions/channel-callbacks/search")
    public CommonResult<PageResult<?>> pageChannelCallbacks(@RequestBody(required = false) ChannelCallbackQuery query) {
        return success(paymentTransactionApplicationService.pageChannelCallbacks(query));
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    @PostMapping("/transactions/merchant-notifications/search")
    public CommonResult<PageResult<?>> pageMerchantNotifications(@RequestBody(required = false) MerchantNotificationQuery query) {
        return success(paymentTransactionApplicationService.pageMerchantNotifications(query));
    }

    /**
     * 触发指定交易时间片的到期商户通知重试。
     * <p>
     * 该接口面向 service-job 或内部补偿任务，不对公网开放，并由 /internal/** HMAC 拦截器校验服务间签名。
     *
     * @param commandDTO 通知扫描命令
     * @return 成功通知数量
     */
    @PostMapping("/transactions/merchant-notifications/notify-due")
    public CommonResult<Integer> notifyDueMerchantNotifications(
            @RequestBody TransactionMerchantNotificationNotifyDueCommandDTO commandDTO) {
        return success(paymentTransactionApplicationService.notifyDueMerchantNotifications(commandDTO));
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

package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionApplicationService
 * @date : 2026-07-14 23:58
 * @email : scott_x@163.com
 * @description : 管理后台交易查询应用服务，位于 service-admin 应用层，编排管理端权限入口与 service-payment 交易分表查询能力。
 * @status : create
 */
@Service
public class AdminTransactionApplicationService {

    /**
     * 管理端退款动作幂等号前缀。
     */
    private static final String ADMIN_REFUND_ORDER_ID_PREFIX = "ADMRF";

    /**
     * 管理端撤销动作幂等号前缀。
     */
    private static final String ADMIN_VOID_ORDER_ID_PREFIX = "ADMVD";

    /**
     * 可作为退款源的交易动作类型。
     */
    private static final Set<String> REFUND_SOURCE_TYPES = Set.of("PAYMENT", "CAPTURE");

    /**
     * 可作为撤销源的授权类动作类型。
     */
    private static final Set<String> VOID_SOURCE_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");

    /**
     * service-payment 内部查询客户端。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建管理后台交易查询应用服务。
     *
     * @param paymentInternalClient service-payment 内部查询客户端
     */
    public AdminTransactionApplicationService(PaymentInternalClient paymentInternalClient) {
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 分页查询交易生命周期主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        return paymentInternalClient.pageOrders(query);
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        return paymentInternalClient.pageOperations(query);
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        return paymentInternalClient.searchOperations(query);
    }

    /**
     * 管理后台发起退款动作。
     * <p>
     * 后台不直接修改交易表，统一转换为 service-payment 后续动作命令，由支付核心执行幂等、状态机和渠道调用。
     *
     * @param transactionId 原平台交易 ID
     * @param request 退款动作请求
     * @return 退款动作结果
     */
    public TransactionActionResponse refund(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(transactionId);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful transactions can be refunded");
        }
        BigDecimal amount = request == null ? null : request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "refund amount must be greater than 0");
        }
        sourceOperation = resolveRefundSourceOperation(detailResponse, sourceOperation);
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                sourceOperation,
                request,
                amount,
                ADMIN_REFUND_ORDER_ID_PREFIX);
        return paymentInternalClient.refund(requestDTO);
    }

    /**
     * 管理后台发起撤销动作。
     * <p>
     * 撤销仍走支付核心后续动作入口，由核心按原交易状态判断是否允许撤销。
     *
     * @param transactionId 原平台交易 ID
     * @param request 撤销动作请求
     * @return 撤销动作结果
     */
    public TransactionActionResponse voidPayment(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(transactionId);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful authorizations can be voided");
        }
        if (!VOID_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                sourceOperation,
                request,
                request == null ? null : request.getAmount(),
                ADMIN_VOID_ORDER_ID_PREFIX);
        return paymentInternalClient.voidPayment(requestDTO);
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    public TransactionDetailResponse detail(String transactionId) {
        return paymentInternalClient.detail(transactionId);
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    public PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query) {
        return paymentInternalClient.pageChannelLogs(query);
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    public PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query) {
        return paymentInternalClient.pageChannelCallbacks(query);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    public PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query) {
        return paymentInternalClient.pageMerchantNotifications(query);
    }

    private PaymentTransactionActionClientRequestDTO buildActionRequest(TransactionOperationResponse sourceOperation,
                                                                       TransactionActionRequest request,
                                                                       BigDecimal amount,
                                                                       String orderIdPrefix) {
        LocalDateTime transactionDateTime = LocalDateTime.now();
        String merchantOrderId = request == null ? null : request.getMerchantOrderId();
        if (!StringUtils.hasText(merchantOrderId)) {
            merchantOrderId = PaymentOrderNoGenerator.nextOrderNo(orderIdPrefix, transactionDateTime);
        }
        PaymentTransactionActionClientRequestDTO requestDTO = new PaymentTransactionActionClientRequestDTO();
        requestDTO.setMerchantId(sourceOperation.getMerchantId());
        requestDTO.setMerchantOrderNo(sourceOperation.getMerchantOrderNo());
        requestDTO.setMerchantOrderId(merchantOrderId);
        requestDTO.setRequestId(merchantOrderId);
        requestDTO.setAmount(amount);
        requestDTO.setCurrency(StringUtils.hasText(request == null ? null : request.getCurrency())
                ? request.getCurrency()
                : sourceOperation.getTransactionCurrency());
        requestDTO.setTransactionDateTime(transactionDateTime);
        PaymentTransactionActionClientRequestDTO.TransactionInfoDTO transactionInfoDTO =
                new PaymentTransactionActionClientRequestDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId(sourceOperation.getTransactionId());
        transactionInfoDTO.setDescription(request == null ? null : request.getReason());
        requestDTO.setTransactionInfo(transactionInfoDTO);
        return requestDTO;
    }

    private TransactionOperationResponse resolveSourceOperation(TransactionDetailResponse detailResponse, String transactionId) {
        if (detailResponse == null || detailResponse.getOperations() == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return detailResponse.getOperations().stream()
                .filter(operation -> transactionId.equals(operation.getTransactionId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    private TransactionOperationResponse resolveRefundSourceOperation(TransactionDetailResponse detailResponse,
                                                                     TransactionOperationResponse selectedOperation) {
        if (REFUND_SOURCE_TYPES.contains(selectedOperation.getTransactionType())) {
            return selectedOperation;
        }
        return detailResponse.getOperations().stream()
                .filter(operation -> REFUND_SOURCE_TYPES.contains(operation.getTransactionType()))
                .filter(operation -> "SUCCESS".equals(operation.getTransactionStatus()))
                .max(Comparator.comparing(TransactionOperationResponse::getOperationTime,
                        Comparator.nullsLast(LocalDateTime::compareTo)))
                .orElseThrow(() -> new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED));
    }
}

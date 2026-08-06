package com.scott.payment.admin.client.payment;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.component.core.model.PageResult;

import java.util.Map;

import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalClientRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignClientCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.ResolveCommand;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-07-14 23:57
 * @email : scott_x@163.com
 * @description : service-payment 内部查询客户端契约，位于 service-admin 客户端层，为交易管理页面封装只读内部接口调用。
 * @status : create
 */
public interface PaymentInternalClient {

    /** 查询退款/撤销分页和统计。 */
    RefundSearchResponse searchRefunds(RefundQuery query);

    /** 查询单笔退款详情。 */
    RefundDetailResponse refundDetail(String transactionId, java.time.LocalDateTime transactionDateTime);

    /** 审批通过退款。 */
    ApprovalResult approveRefund(String approvalId, ApprovalClientRequest request);

    /** 拒绝退款审批。 */
    ApprovalResult rejectRefund(String approvalId, ApprovalClientRequest request);

    /** 查询勾兑异常分页和统计。 */
    AbnormalSearchResponse searchChannelMatchAbnormalities(AbnormalQuery query);

    /** 查询勾兑异常详情。 */
    AbnormalDetailResponse channelMatchAbnormalityDetail(String eventId,
                                                         java.time.LocalDateTime transactionDateTime);

    /** 领取或转派勾兑异常案件。 */
    AbnormalRecord assignChannelMatchAbnormality(String eventId, AssignClientCommand command);

    /** 单笔重新勾兑。 */
    AbnormalRecord requeryChannelMatchAbnormality(String eventId, RequeryCommand command);

    /** 批量重新勾兑。 */
    BatchRequeryResult batchRequeryChannelMatchAbnormalities(BatchRequeryCommand command);

    /** 关闭或忽略勾兑异常案件。 */
    AbnormalRecord resolveChannelMatchAbnormality(String eventId, ResolveCommand command);

    /**
     * 分页查询交易主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起退款动作。
     *
     * @param commandDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param commandDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表返回的当前动作真实分片时间
     * @param rootTransactionDateTime 列表返回的生命周期根主单真实分片时间
     * @return 交易聚合详情
     */
    TransactionDetailResponse detail(String transactionId,
                                     java.time.LocalDateTime transactionDateTime,
                                     java.time.LocalDateTime rootTransactionDateTime);

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);
}

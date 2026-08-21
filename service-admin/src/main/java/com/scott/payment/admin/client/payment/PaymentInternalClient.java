package com.scott.payment.admin.client.payment;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryResponse;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;

import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalClientRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord;
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
 * @description : service-payment 内部命令客户端契约，只承载需要支付核心修改交易、退款审批或异常案件状态的操作。
 * @status : create
 */
public interface PaymentInternalClient {

    /** 审批通过退款。 */
    ApprovalResult approveRefund(String approvalId, ApprovalClientRequest request);

    /** 拒绝退款审批。 */
    ApprovalResult rejectRefund(String approvalId, ApprovalClientRequest request);

    /** 领取或转派勾兑异常案件。 */
    AbnormalRecord assignChannelMatchAbnormality(String eventId, AssignClientCommand command);

    /** 单笔重新勾兑。 */
    AbnormalRecord requeryChannelMatchAbnormality(String eventId, RequeryCommand command);

    /** 批量重新勾兑。 */
    BatchRequeryResult batchRequeryChannelMatchAbnormalities(BatchRequeryCommand command);

    /** 使用交易真实分片时间主动重查并勾兑单笔交易。 */
    ChannelMatchRequeryResponse requeryChannelMatch(String transactionId, ChannelMatchRequeryRequest request);

    /** 关闭或忽略勾兑异常案件。 */
    AbnormalRecord resolveChannelMatchAbnormality(String eventId, ResolveCommand command);

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
     * @param requestDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param requestDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO);

}

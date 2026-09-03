package com.scott.payment.clearing.service;

import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveAdjustmentService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 经双人复核的保证金标签币种差额调整边界；不读取汇率、不写商户余额。
 * @status : update
 */
public interface ReserveAdjustmentService {

    /** 人工复核决定；只允许批准或拒绝，不能由调用方直接传入持久化终态。 */
    enum ReviewDecision {
        /**
         * APPROVE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        APPROVE,
        REJECT
    }

    /**
     * 提交标签币种保证金差额申请，仅冻结当时的保证金状态版本，不生成资金事实。
     *
     * @param command 幂等请求键、原保证金身份、方向、金额和提交人审计信息
     * @return 待复核申请；相同请求键重复提交返回同一业务结果
     * @throws IllegalArgumentException 方向、金额、释放日期或审计参数不合法时抛出
     * @throws IllegalStateException 原保证金身份或预期版本不匹配时抛出
     */
    ReserveAdjustmentResult submit(SubmitCommand command);

    /**
     * 按申请版本执行双人复核；批准时在同一事务追加调整事实、更新余额状态和创建结算候选。
     *
     * @param command 申请号、预期申请版本、复核决定和可信复核人
     * @return 拒绝结果或已资金化的调整结果
     * @throws IllegalArgumentException 复核命令不完整时抛出
     * @throws IllegalStateException 同人复核、版本过期或保证金状态冲突时抛出
     */
    ReserveAdjustmentResult review(ReviewCommand command);

    /**
     * 保证金调整提交命令。
     *
     * @param requestKey 调用方幂等键，不允许为空
     * @param reserveStateId 原保证金状态业务号
     * @param originalTransactionId 原支付交易号
     * @param originalTransactionDateTime 原支付季度分片时间
     * @param expectedReserveStateVersion 提交时冻结的保证金状态版本
     * @param direction 标签币种保证金增加或扣减方向
     * @param adjustmentAmount 标签币种正数调整金额
     * @param requestedReleaseDate 增加保证金的计划释放日；扣减时为空
     * @param reason 运营提交原因，不得包含敏感支付数据
     * @param submitOperator service-admin 注入的可信提交人
     * @param requestedInstant 请求审计时点
     */
    record SubmitCommand(String requestKey,
                         String reserveStateId,
                         String originalTransactionId,
                         LocalDateTime originalTransactionDateTime,
                         long expectedReserveStateVersion,
                         ReserveAdjustmentDirection direction,
                         BigDecimal adjustmentAmount,
                         LocalDate requestedReleaseDate,
                         String reason,
                         String submitOperator,
                         Instant requestedInstant) {
    }

    /**
     * 保证金调整复核命令。
     *
     * @param adjustmentNo 调整申请号
     * @param expectedRequestVersion 申请状态 CAS 版本
     * @param decision 固定复核决定
     * @param reviewComment 复核意见，不得包含敏感支付数据
     * @param reviewOperator service-admin 注入的可信复核人，必须与提交人不同
     * @param reviewInstant 复核审计时点
     */
    record ReviewCommand(String adjustmentNo,
                         long expectedRequestVersion,
                         ReviewDecision decision,
                         String reviewComment,
                         String reviewOperator,
                         Instant reviewInstant) {
    }

    /**
     * 保证金调整命令结果。
     *
     * @param adjustmentNo 调整申请号
     * @param status 已持久化申请状态
     * @param transactionId 批准后生成的独立调整动作号；未资金化时为空
     * @param sourceRevision 保证金状态资金化修订；未资金化时为零
     * @param version 当前申请 CAS 版本
     */
    record ReserveAdjustmentResult(String adjustmentNo,
                                   String status,
                                   String transactionId,
                                   int sourceRevision,
                                   long version) {
    }
}

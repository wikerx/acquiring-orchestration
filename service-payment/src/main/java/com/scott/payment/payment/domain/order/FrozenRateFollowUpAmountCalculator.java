package com.scott.payment.payment.domain.order;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 使用原交易冻结汇率计算请款、退款、撤销和增量授权的标签金额及渠道交易金额。
 *
 * <p>标签金额始终保持商户请求口径，交易金额按原交易汇率换算。最后一笔剩余请款直接使用
 * 主单剩余可请款交易金额，避免多次四舍五入造成累计金额多一分或少一分。</p>
 */
public final class FrozenRateFollowUpAmountCalculator {

    /**
     * 未发生换汇时使用的固定汇率。
     */
    private static final BigDecimal UNIT_RATE = new BigDecimal("1.00000000");

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private FrozenRateFollowUpAmountCalculator() {
    }

    /**
     * 计算请款或预授权完成的标签金额和渠道交易金额。
     *
     * @param sourceOrderDO 已锁定的原授权生命周期主单
     * @param lifecycleOperations 同一生命周期内已经持久化的动作事实
     * @param requestAmount 商户本次请求的标签金额
     * @param requestCurrency 商户本次请求币种，可为空
     * @param labelCurrencyExponent 标签币种小数位
     * @param transactionCurrencyExponent 渠道交易币种小数位
     * @return 使用原交易冻结汇率生成的本次金额方案
     */
    public static AmountPlan calculateCapture(TransactionOrderDO sourceOrderDO,
                                              List<TransactionOperationDO> lifecycleOperations,
                                              BigDecimal requestAmount,
                                              String requestCurrency,
                                              int labelCurrencyExponent,
                                              int transactionCurrencyExponent) {
        SourceMoney sourceMoney = sourceMoney(sourceOrderDO, labelCurrencyExponent);
        String labelCurrency = validateRequestCurrency(requestCurrency, sourceMoney.labelCurrency(), true);
        validatePositiveAmount(requestAmount);

        BigDecimal remainingLabelAmount = availableLabelCaptureAmount(
                sourceOrderDO, lifecycleOperations, sourceMoney, labelCurrencyExponent);
        if (remainingLabelAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "captured label amount exceeds authorized label amount");
        }
        if (requestAmount.compareTo(remainingLabelAmount) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "CAPTURE amount exceeds available label amount");
        }

        BigDecimal availableTransactionAmount = availableCaptureAmount(sourceOrderDO);
        boolean sameCurrency = sourceMoney.labelCurrency().equals(sourceMoney.transactionCurrency());
        boolean finalRemainingCapture = requestAmount.compareTo(remainingLabelAmount) == 0
                || sameCurrency && requestAmount.compareTo(availableTransactionAmount) == 0;
        BigDecimal transactionAmount = finalRemainingCapture
                ? availableTransactionAmount
                : convert(requestAmount, sourceMoney.transactionRate(), transactionCurrencyExponent);
        validateTransactionAmount(transactionAmount, availableTransactionAmount, finalRemainingCapture);
        return sourceMoney.toPlan(labelCurrency, requestAmount, transactionAmount);
    }

    /**
     * 计算退款的标签金额和渠道交易金额。
     *
     * <p>已完成退款由主单双口径累计字段反映，非终态退款按动作单两套金额分别预占。
     * 最后一笔标签余额退款直接使用剩余渠道金额，吸收历次换算产生的舍入差额。</p>
     *
     * @param sourceOrderDO 已锁定的原支付生命周期主单
     * @param nonTerminalRefunds 同一生命周期内全部非终态退款动作
     * @param requestAmount 商户本次请求的标签金额
     * @param requestCurrency 商户本次请求币种，可为空
     * @param labelCurrencyExponent 标签币种小数位
     * @param transactionCurrencyExponent 渠道交易币种小数位
     * @return 使用原交易冻结汇率生成的本次退款金额方案
     */
    public static AmountPlan calculateRefund(TransactionOrderDO sourceOrderDO,
                                             List<TransactionOperationDO> nonTerminalRefunds,
                                             BigDecimal requestAmount,
                                             String requestCurrency,
                                             int labelCurrencyExponent,
                                             int transactionCurrencyExponent) {
        SourceMoney sourceMoney = sourceMoney(sourceOrderDO, labelCurrencyExponent);
        String labelCurrency = validateRequestCurrency(requestCurrency, sourceMoney.labelCurrency(), true);
        validatePositiveAmount(requestAmount);

        BigDecimal availableLabelAmount = availableLabelRefundAmount(sourceOrderDO, sourceMoney);
        BigDecimal availableTransactionAmount = availableRefundAmount(sourceOrderDO);
        BigDecimal pendingLabelAmount = BigDecimal.ZERO;
        BigDecimal pendingTransactionAmount = BigDecimal.ZERO;
        for (TransactionOperationDO operationDO : safeOperations(nonTerminalRefunds)) {
            if (!PaymentTransactionTypeEnum.REFUND.getCode().equals(operationDO.getTransactionType())
                    || isTerminal(operationDO)) {
                continue;
            }
            pendingLabelAmount = pendingLabelAmount.add(resolveOperationLabelAmount(
                    operationDO, sourceMoney, labelCurrencyExponent));
            pendingTransactionAmount = pendingTransactionAmount.add(requireOperationTransactionAmount(operationDO));
        }

        BigDecimal remainingLabelAmount = availableLabelAmount.subtract(pendingLabelAmount);
        BigDecimal remainingTransactionAmount = availableTransactionAmount.subtract(pendingTransactionAmount);
        if (remainingLabelAmount.compareTo(BigDecimal.ZERO) < 0
                || remainingTransactionAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "pending refund amount exceeds available amount");
        }
        if (requestAmount.compareTo(remainingLabelAmount) > 0) {
            throw new ServiceException(ApiResultEnum.REFUND_AMOUNT_EXCEEDS_AVAILABLE);
        }

        boolean sameCurrency = sourceMoney.labelCurrency().equals(sourceMoney.transactionCurrency());
        boolean finalRemainingRefund = requestAmount.compareTo(remainingLabelAmount) == 0
                || sameCurrency && requestAmount.compareTo(remainingTransactionAmount) == 0;
        BigDecimal transactionAmount = finalRemainingRefund
                ? remainingTransactionAmount
                : convert(requestAmount, sourceMoney.transactionRate(), transactionCurrencyExponent);
        validateRefundTransactionAmount(transactionAmount, remainingTransactionAmount, finalRemainingRefund);
        return sourceMoney.toPlan(labelCurrency, requestAmount, transactionAmount);
    }

    /**
     * 计算全额撤销的标签金额和渠道交易金额。
     *
     * <p>撤销释放的是原授权生命周期的全部剩余额度，不支持部分撤销。商户显式传入金额时，
     * 只能等于标签口径剩余授权金额；渠道请求始终使用渠道口径剩余授权金额。跨币种历史交易
     * 缺少标签累计事实时拒绝处理，禁止通过渠道金额和汇率反推商户金额。</p>
     *
     * @param sourceOrderDO 已锁定的原授权生命周期主单
     * @param requestAmount 商户可选的本次撤销标签金额
     * @param requestCurrency 商户可选的本次撤销标签币种
     * @return 沿用原交易冻结汇率元数据的双口径撤销金额方案
     */
    public static AmountPlan calculateVoid(TransactionOrderDO sourceOrderDO,
                                           BigDecimal requestAmount,
                                           String requestCurrency) {
        SourceMoney sourceMoney = sourceMoney(sourceOrderDO, 0);
        String labelCurrency = validateRequestCurrency(requestCurrency, sourceMoney.labelCurrency(), true);
        BigDecimal labelAmount = remainingLabelAuthorizedAmount(sourceOrderDO, sourceMoney);
        BigDecimal transactionAmount = remainingTransactionAuthorizedAmount(sourceOrderDO);
        if (labelAmount.compareTo(BigDecimal.ZERO) <= 0
                || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "VOID available amount must be greater than zero");
        }
        if (requestAmount != null) {
            validatePositiveAmount(requestAmount);
            if (requestAmount.compareTo(labelAmount) != 0) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                        "VOID only supports the full remaining label amount");
            }
        }
        return sourceMoney.toPlan(labelCurrency, labelAmount, transactionAmount);
    }

    /**
     * 计算增量授权的标签金额和渠道交易金额。
     *
     * @param sourceOrderDO 已锁定的原授权生命周期主单
     * @param requestAmount 商户本次请求的标签金额
     * @param requestCurrency 商户本次请求币种
     * @param labelCurrencyExponent 标签币种小数位
     * @param transactionCurrencyExponent 渠道交易币种小数位
     * @return 使用原交易冻结汇率生成的本次金额方案
     */
    public static AmountPlan calculateIncrementalAuthorization(TransactionOrderDO sourceOrderDO,
                                                               BigDecimal requestAmount,
                                                               String requestCurrency,
                                                               int labelCurrencyExponent,
                                                               int transactionCurrencyExponent) {
        SourceMoney sourceMoney = sourceMoney(sourceOrderDO, labelCurrencyExponent);
        String labelCurrency = validateRequestCurrency(requestCurrency, sourceMoney.labelCurrency(), false);
        validatePositiveAmount(requestAmount);
        BigDecimal transactionAmount = convert(
                requestAmount, sourceMoney.transactionRate(), transactionCurrencyExponent);
        if (transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "INCREMENTAL_AUTHORIZATION amount is below transaction currency precision");
        }
        return sourceMoney.toPlan(labelCurrency, requestAmount, transactionAmount);
    }

    /**
     * 从原交易冻结事实构建金额换算上下文。
     */
    private static SourceMoney sourceMoney(TransactionOrderDO sourceOrderDO, int labelCurrencyExponent) {
        if (sourceOrderDO == null || !StringUtils.hasText(sourceOrderDO.getTransactionCurrency())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "source transaction currency is empty");
        }
        String transactionCurrency = normalizeCurrency(sourceOrderDO.getTransactionCurrency());
        String labelCurrency = StringUtils.hasText(sourceOrderDO.getLabelCurrency())
                ? normalizeCurrency(sourceOrderDO.getLabelCurrency()) : transactionCurrency;
        BigDecimal transactionRate = resolveTransactionRate(sourceOrderDO, labelCurrency, transactionCurrency);
        BigDecimal labelAmount = sourceOrderDO.getLabelAmount();
        if (labelAmount == null) {
            if (sourceOrderDO.getTransactionAmount() == null) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                        "source transaction amount is empty");
            }
            if (!transactionCurrency.equals(labelCurrency)) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                        "cross-currency source label amount is missing");
            }
            labelAmount = sourceOrderDO.getTransactionAmount();
        }
        return new SourceMoney(
                labelCurrency,
                labelAmount,
                transactionCurrency,
                transactionRate,
                sourceOrderDO.getRateSource(),
                sourceOrderDO.getRateTime(),
                sourceOrderDO.getDccEnabled(),
                sourceOrderDO.getEdcEnabled());
    }

    /**
     * 解析交易汇率。
     */
    private static BigDecimal resolveTransactionRate(TransactionOrderDO sourceOrderDO,
                                                     String labelCurrency,
                                                     String transactionCurrency) {
        BigDecimal transactionRate = sourceOrderDO.getTransactionRate();
        if (transactionRate != null && transactionRate.compareTo(BigDecimal.ZERO) > 0) {
            return transactionRate;
        }
        if (labelCurrency.equals(transactionCurrency)) {
            return UNIT_RATE;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                "source transaction rate is missing");
    }

    /**
     * 校验后续动作币种与原交易标签币种一致。
     */
    private static String validateRequestCurrency(String requestCurrency,
                                                  String sourceLabelCurrency,
                                                  boolean optional) {
        if (!StringUtils.hasText(requestCurrency)) {
            if (optional) {
                return sourceLabelCurrency;
            }
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "request currency is required");
        }
        String normalizedRequestCurrency = normalizeCurrency(requestCurrency);
        if (!sourceLabelCurrency.equals(normalizedRequestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "request currency must match source label currency");
        }
        return normalizedRequestCurrency;
    }

    /**
     * 校验正值金额。
     */
    private static void validatePositiveAmount(BigDecimal requestAmount) {
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "request amount must be greater than zero");
        }
    }

    /**
     * 解析动作标签金额。
     */
    private static BigDecimal resolveOperationLabelAmount(TransactionOperationDO operationDO,
                                                          SourceMoney sourceMoney,
                                                          int labelCurrencyExponent) {
        if (StringUtils.hasText(operationDO.getLabelCurrency())
                && !sourceMoney.labelCurrency().equals(normalizeCurrency(operationDO.getLabelCurrency()))) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "follow-up label currency differs from source transaction");
        }
        if (operationDO.getLabelAmount() != null) {
            return operationDO.getLabelAmount();
        }
        if (operationDO.getTransactionAmount() == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "follow-up amount fact is incomplete");
        }
        if (!sourceMoney.labelCurrency().equals(sourceMoney.transactionCurrency())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "cross-currency follow-up label amount is missing");
        }
        return operationDO.getTransactionAmount();
    }

    /**
     * 优先读取主单双口径累计字段；迁移窗口内旧数据才回退到动作事实重算。
     */
    private static BigDecimal availableLabelCaptureAmount(TransactionOrderDO sourceOrderDO,
                                                          List<TransactionOperationDO> lifecycleOperations,
                                                          SourceMoney sourceMoney,
                                                          int labelCurrencyExponent) {
        if (sourceOrderDO.getLabelAvailableCaptureAmount() != null) {
            return sourceOrderDO.getLabelAvailableCaptureAmount();
        }
        BigDecimal authorizedLabelAmount = sourceMoney.labelAmount();
        BigDecimal capturedLabelAmount = BigDecimal.ZERO;
        for (TransactionOperationDO operationDO : safeOperations(lifecycleOperations)) {
            if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())) {
                continue;
            }
            if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(operationDO.getTransactionType())) {
                authorizedLabelAmount = authorizedLabelAmount.add(resolveOperationLabelAmount(
                        operationDO, sourceMoney, labelCurrencyExponent));
            } else if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(operationDO.getTransactionType())
                    || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(operationDO.getTransactionType())) {
                capturedLabelAmount = capturedLabelAmount.add(resolveOperationLabelAmount(
                        operationDO, sourceMoney, labelCurrencyExponent));
            }
        }
        return authorizedLabelAmount.subtract(capturedLabelAmount);
    }

    /**
     * 解析主单标签口径可退款余额。
     */
    private static BigDecimal availableLabelRefundAmount(TransactionOrderDO sourceOrderDO,
                                                         SourceMoney sourceMoney) {
        if (sourceOrderDO.getLabelAvailableRefundAmount() != null) {
            return sourceOrderDO.getLabelAvailableRefundAmount();
        }
        if (sourceMoney.labelCurrency().equals(sourceMoney.transactionCurrency())) {
            return availableRefundAmount(sourceOrderDO);
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                "cross-currency source label refund balance is missing");
    }

    /**
     * 解析主单渠道口径可退款余额。
     */
    private static BigDecimal availableRefundAmount(TransactionOrderDO sourceOrderDO) {
        return sourceOrderDO.getAvailableRefundAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAvailableRefundAmount();
    }

    /**
     * 解析主单标签口径剩余可撤销授权金额。
     */
    private static BigDecimal remainingLabelAuthorizedAmount(TransactionOrderDO sourceOrderDO,
                                                             SourceMoney sourceMoney) {
        if (sourceOrderDO.getLabelAuthorizedAmount() != null) {
            return sourceOrderDO.getLabelAuthorizedAmount()
                    .subtract(zeroIfNull(sourceOrderDO.getLabelAuthorizedCancelAmount()));
        }
        if (sourceMoney.labelCurrency().equals(sourceMoney.transactionCurrency())) {
            return remainingTransactionAuthorizedAmount(sourceOrderDO);
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                "cross-currency source label authorization balance is missing");
    }

    /**
     * 解析主单渠道口径剩余可撤销授权金额。
     */
    private static BigDecimal remainingTransactionAuthorizedAmount(TransactionOrderDO sourceOrderDO) {
        return zeroIfNull(sourceOrderDO.getAuthorizedAmount())
                .subtract(zeroIfNull(sourceOrderDO.getAuthorizedCancelAmount()));
    }

    /**
     * 读取非终态动作的渠道金额；不完整的资金事实不得被静默忽略。
     */
    private static BigDecimal requireOperationTransactionAmount(TransactionOperationDO operationDO) {
        if (operationDO.getTransactionAmount() == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "follow-up transaction amount fact is incomplete");
        }
        return operationDO.getTransactionAmount();
    }

    /**
     * 判断动作是否已进入成功或失败终态。
     */
    private static boolean isTerminal(TransactionOperationDO operationDO) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus());
    }

    /**
     * 计算原交易剩余可请款金额。
     */
    private static BigDecimal availableCaptureAmount(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO.getAvailableCaptureAmount() != null) {
            return sourceOrderDO.getAvailableCaptureAmount();
        }
        BigDecimal authorizedAmount = sourceOrderDO.getAuthorizedAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getAuthorizedAmount();
        BigDecimal capturedAmount = sourceOrderDO.getCapturedAmount() == null
                ? BigDecimal.ZERO : sourceOrderDO.getCapturedAmount();
        return authorizedAmount.subtract(capturedAmount);
    }

    /**
     * 按冻结交易汇率换算后续动作金额。
     */
    private static BigDecimal convert(BigDecimal labelAmount,
                                      BigDecimal transactionRate,
                                      int transactionCurrencyExponent) {
        return labelAmount.multiply(transactionRate)
                .setScale(transactionCurrencyExponent, RoundingMode.HALF_UP);
    }

    /**
     * 校验换算后的交易金额不超过可用余额。
     */
    private static void validateTransactionAmount(BigDecimal transactionAmount,
                                                  BigDecimal availableTransactionAmount,
                                                  boolean finalRemainingCapture) {
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "CAPTURE amount is below transaction currency precision");
        }
        if (availableTransactionAmount == null || transactionAmount.compareTo(availableTransactionAmount) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "CAPTURE amount exceeds available transaction amount");
        }
        if (!finalRemainingCapture && transactionAmount.compareTo(availableTransactionAmount) == 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "partial CAPTURE amount rounds to the full remaining transaction amount");
        }
    }

    /**
     * 校验退款渠道金额未透支预占后的剩余额度。
     */
    private static void validateRefundTransactionAmount(BigDecimal transactionAmount,
                                                        BigDecimal availableTransactionAmount,
                                                        boolean finalRemainingRefund) {
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "REFUND amount is below transaction currency precision");
        }
        if (availableTransactionAmount == null || transactionAmount.compareTo(availableTransactionAmount) > 0) {
            throw new ServiceException(ApiResultEnum.REFUND_AMOUNT_EXCEEDS_AVAILABLE);
        }
        if (!finalRemainingRefund && transactionAmount.compareTo(availableTransactionAmount) == 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "partial REFUND amount rounds to the full remaining transaction amount");
        }
    }

    /**
     * 将空动作集合转换为空列表。
     */
    private static List<TransactionOperationDO> safeOperations(List<TransactionOperationDO> operations) {
        return operations == null ? List.of() : operations;
    }

    /**
     * 将空金额转换为零，累计金额计算不允许传播空值。
     */
    private static BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 规范化币种。
     */
    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 一笔后续交易同时使用的商户标签金额和渠道交易金额。
     *
     * @param labelCurrency 商户标签币种
     * @param labelAmount 商户标签金额
     * @param transactionCurrency 渠道交易币种
     * @param transactionAmount 渠道交易金额
     * @param transactionRate 首笔交易冻结汇率
     * @param rateSource 首笔交易汇率来源
     * @param rateTime 首笔交易汇率时间
     * @param dccEnabled 首笔交易 DCC 标志
     * @param edcEnabled 首笔交易 EDC 标志
     */
    public record AmountPlan(String labelCurrency,
                             BigDecimal labelAmount,
                             String transactionCurrency,
                             BigDecimal transactionAmount,
                             BigDecimal transactionRate,
                             String rateSource,
                             LocalDateTime rateTime,
                             Integer dccEnabled,
                             Integer edcEnabled) {
    }

    /**
     * 封装原交易冻结金额及汇率上下文。
     */
    private record SourceMoney(String labelCurrency,
                               BigDecimal labelAmount,
                               String transactionCurrency,
                               BigDecimal transactionRate,
                               String rateSource,
                               LocalDateTime rateTime,
                               Integer dccEnabled,
                               Integer edcEnabled) {

        /**
         * 构建后续动作金额计划。
         */
        private AmountPlan toPlan(String requestLabelCurrency,
                                  BigDecimal requestLabelAmount,
                                  BigDecimal requestTransactionAmount) {
            return new AmountPlan(
                    requestLabelCurrency,
                    requestLabelAmount,
                    transactionCurrency,
                    requestTransactionAmount,
                    transactionRate,
                    rateSource,
                    rateTime,
                    dccEnabled,
                    edcEnabled);
        }
    }
}

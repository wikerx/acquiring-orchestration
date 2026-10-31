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
 * 使用原交易冻结汇率计算请款和增量授权的标签金额及渠道交易金额。
 *
 * <p>标签金额始终保持商户请求口径，交易金额按原交易汇率换算。最后一笔剩余请款直接使用
 * 主单剩余可请款交易金额，避免多次四舍五入造成累计金额多一分或少一分。</p>
 */
public final class FrozenRateFollowUpAmountCalculator {

    /**
     * 未发生换汇时使用的固定汇率。
     */
    private static final BigDecimal UNIT_RATE = new BigDecimal("1.00000000");

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

        BigDecimal authorizedLabelAmount = sourceMoney.labelAmount();
        BigDecimal capturedLabelAmount = BigDecimal.ZERO;
        for (TransactionOperationDO operationDO : safeOperations(lifecycleOperations)) {
            if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())) {
                continue;
            }
            if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(operationDO.getTransactionType())) {
                authorizedLabelAmount = authorizedLabelAmount.add(resolveOperationLabelAmount(
                        operationDO, sourceMoney, labelCurrencyExponent));
                continue;
            }
            if (PaymentTransactionTypeEnum.CAPTURE.getCode().equals(operationDO.getTransactionType())
                    || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode().equals(operationDO.getTransactionType())) {
                capturedLabelAmount = capturedLabelAmount.add(resolveOperationLabelAmount(
                        operationDO, sourceMoney, labelCurrencyExponent));
            }
        }

        BigDecimal remainingLabelAmount = authorizedLabelAmount.subtract(capturedLabelAmount);
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
            labelAmount = transactionCurrency.equals(labelCurrency)
                    ? sourceOrderDO.getTransactionAmount()
                    : sourceOrderDO.getTransactionAmount().divide(
                            transactionRate, labelCurrencyExponent, RoundingMode.HALF_UP);
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

    private static void validatePositiveAmount(BigDecimal requestAmount) {
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "request amount must be greater than zero");
        }
    }

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
        return operationDO.getTransactionAmount().divide(
                sourceMoney.transactionRate(), labelCurrencyExponent, RoundingMode.HALF_UP);
    }

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

    private static BigDecimal convert(BigDecimal labelAmount,
                                      BigDecimal transactionRate,
                                      int transactionCurrencyExponent) {
        return labelAmount.multiply(transactionRate)
                .setScale(transactionCurrencyExponent, RoundingMode.HALF_UP);
    }

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

    private static List<TransactionOperationDO> safeOperations(List<TransactionOperationDO> operations) {
        return operations == null ? List.of() : operations;
    }

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

    private record SourceMoney(String labelCurrency,
                               BigDecimal labelAmount,
                               String transactionCurrency,
                               BigDecimal transactionRate,
                               String rateSource,
                               LocalDateTime rateTime,
                               Integer dccEnabled,
                               Integer edcEnabled) {

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

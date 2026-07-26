package com.scott.payment.payment.domain.state;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionStateMachineService
 * @date : 2026-07-14 19:35
 * @email : scott_x@163.com
 * @description : 收单交易状态机默认实现，位于 service-payment 领域状态层，集中保护授权、请款、退款和撤销等资金动作的状态与金额边界。
 * @status : create
 */
@Service
public class DefaultTransactionStateMachineService implements TransactionStateMachineService {

    /**
     * 允许发起增量授权的原始交易类型。
     */
    private static final Set<String> INCREMENTAL_AUTHORIZATION_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode()
    );

    /**
     * 允许发起请款的原始交易类型。
     */
    private static final Set<String> CAPTURE_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode()
    );

    /**
     * 允许发起退款的原始交易类型。
     */
    private static final Set<String> REFUND_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.PAYMENT.getCode(),
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode()
    );

    /**
     * 允许发起撤销的原始交易类型。
     */
    private static final Set<String> VOID_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PAYMENT.getCode()
    );

    /**
     * 校验后续交易动作是否允许发起。
     *
     * @param sourceOrderDO 原交易生命周期主单
     * @param nextTransactionType 后续交易类型
     * @param requestAmount 本次请求金额
     */
    @Override
    public void validateFollowUpAction(TransactionOrderDO sourceOrderDO,
                                       PaymentTransactionTypeEnum nextTransactionType,
                                       BigDecimal requestAmount,
                                       String requestCurrency) {
        validateSourceOrder(sourceOrderDO);
        if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION == nextTransactionType) {
            validateType(sourceOrderDO, INCREMENTAL_AUTHORIZATION_SOURCE_TYPES, nextTransactionType);
            validateCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validatePositiveAmount(requestAmount, nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.CAPTURE == nextTransactionType
                || PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION == nextTransactionType) {
            validateType(sourceOrderDO, CAPTURE_SOURCE_TYPES, nextTransactionType);
            validateCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validateAvailableAmount(requestAmount, sourceOrderDO.getAvailableCaptureAmount(), nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.REFUND == nextTransactionType) {
            validateType(sourceOrderDO, REFUND_SOURCE_TYPES, nextTransactionType);
            validateOptionalCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validateAvailableAmount(requestAmount, sourceOrderDO.getAvailableRefundAmount(), nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.VOID == nextTransactionType) {
            validateType(sourceOrderDO, VOID_SOURCE_TYPES, nextTransactionType);
            validateVoidAmount(sourceOrderDO);
            return;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
    }

    private void validateSourceOrder(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(sourceOrderDO.getTransactionStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction status does not allow follow-up action");
        }
        if (!StringUtils.hasText(sourceOrderDO.getTransactionCurrency())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction currency is empty");
        }
    }

    private void validateType(TransactionOrderDO sourceOrderDO,
                              Set<String> allowedSourceTypes,
                              PaymentTransactionTypeEnum nextTransactionType) {
        if (!allowedSourceTypes.contains(sourceOrderDO.getTransactionType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    nextTransactionType.getCode() + " is not allowed for source transaction type");
        }
    }

    /**
     * 校验positive金额输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param requestAmount 金额值，单位必须结合 currency 或同名币种字段解释
     * @param nextTransactionType next Transaction Type 输入值，参与 next交易type 的查询、校验、转换、写入或日志摘要
     */
    private void validatePositiveAmount(BigDecimal requestAmount, PaymentTransactionTypeEnum nextTransactionType) {
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " amount must be greater than zero");
        }
    }

/**
 * 校验币种输入，发现缺失、越权或格式错误时中断当前流程。
 * <p>
 * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
 * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
 * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
 * </p>
 * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
 * @param requestCurrency 币种代码，格式为 ISO 4217 三位大写字母
 * @param nextTransactionType next Transaction Type 输入值，参与 next交易type 的查询、校验、转换、写入或日志摘要
 */
    private void validateCurrency(TransactionOrderDO sourceOrderDO,
                                  String requestCurrency,
                                  PaymentTransactionTypeEnum nextTransactionType) {
        if (!StringUtils.hasText(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency is required");
        }
        if (!sourceOrderDO.getTransactionCurrency().equalsIgnoreCase(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency must match source transaction currency");
        }
    }

/**
 * 校验optional币种输入，发现缺失、越权或格式错误时中断当前流程。
 * <p>
 * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
 * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
 * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
 * </p>
 * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
 * @param requestCurrency 币种代码，格式为 ISO 4217 三位大写字母
 * @param nextTransactionType next Transaction Type 输入值，参与 next交易type 的查询、校验、转换、写入或日志摘要
 */
    private void validateOptionalCurrency(TransactionOrderDO sourceOrderDO,
                                          String requestCurrency,
                                          PaymentTransactionTypeEnum nextTransactionType) {
        if (!StringUtils.hasText(requestCurrency)) {
            return;
        }
        if (!sourceOrderDO.getTransactionCurrency().equalsIgnoreCase(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency must match source transaction currency");
        }
    }

/**
 * 校验available金额输入，发现缺失、越权或格式错误时中断当前流程。
 * <p>
 * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
 * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
 * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
 * </p>
 * @param requestAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param availableAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param nextTransactionType next Transaction Type 输入值，参与 next交易type 的查询、校验、转换、写入或日志摘要
 */
    private void validateAvailableAmount(BigDecimal requestAmount,
                                         BigDecimal availableAmount,
                                         PaymentTransactionTypeEnum nextTransactionType) {
        validatePositiveAmount(requestAmount, nextTransactionType);
        BigDecimal safeAvailableAmount = availableAmount == null ? BigDecimal.ZERO : availableAmount;
        if (requestAmount.compareTo(safeAvailableAmount) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " amount exceeds available amount");
        }
    }

    /**
     * 校验void金额输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 支付核心服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param sourceOrderDO source Order DO 输入值，参与 来源订单do 的查询、校验、转换、写入或日志摘要
     */
    private void validateVoidAmount(TransactionOrderDO sourceOrderDO) {
        BigDecimal capturedAmount = sourceOrderDO.getCapturedAmount() == null ? BigDecimal.ZERO : sourceOrderDO.getCapturedAmount();
        BigDecimal refundedAmount = sourceOrderDO.getRefundedAmount() == null ? BigDecimal.ZERO : sourceOrderDO.getRefundedAmount();
        if (capturedAmount.compareTo(BigDecimal.ZERO) > 0 || refundedAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "captured or refunded transaction can not be voided");
        }
    }
}

package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultChannelTransactionStatusResolver
 * @date : 2026-07-19 22:00
 * @email : scott_x@163.com
 * @description : 默认渠道状态解析器，位于 service-payment 服务实现层，负责把渠道统一状态转换为平台状态；这里集中隔离 WPGXML/WPGJSON 的 AUTHORISED 与 CAPTURED 语义，避免渠道适配层直接决定资金终态。
 * @status : create
 */
@Service
public class DefaultChannelTransactionStatusResolver implements ChannelTransactionStatusResolver {

    private static final Set<String> WORLDPAY_CHANNELS = Set.of("WPGXML", "WPGJSON");

    private static final Set<String> WORLDPAY_AUTHORISED_STATUSES = Set.of(
            "AUTHORISED", "AUTHORIZED", "AUTHORISE", "AUTHORIZE"
    );

    private static final Set<String> WORLDPAY_CAPTURED_STATUSES = Set.of(
            "CAPTURED", "CAPTURED_OK", "SETTLED", "SETTLEMENT_REQUESTED"
    );

    private static final Set<String> WORLDPAY_REFUND_SUCCESS_STATUSES = Set.of(
            "REFUNDED", "REFUND", "SENT_FOR_REFUND"
    );

    private static final Set<String> WORLDPAY_FAILED_STATUSES = Set.of(
            "REFUSED", "ERROR", "FAILED", "FAILURE", "CANCELLED", "CANCELED", "EXPIRED", "DECLINED"
    );

    private static final Set<String> WORLDPAY_PENDING_STATUSES = Set.of(
            "PENDING", "PROCESSING", "SENT_FOR_AUTHORISATION", "SENT_FOR_AUTHORIZATION",
            "SHOPPER_REDIRECTED", "OPEN", "UNKNOWN"
    );

    /**
     * 解析渠道同步响应。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param response 渠道同步响应
     * @return 平台状态解析结果
     */
    @Override
    public ChannelTransactionStatusResolution resolveSync(String channelCode,
                                                          String transactionType,
                                                          ChannelPaymentResponse response) {
        if (response == null) {
            return processing();
        }
        if (isWorldPay(channelCode, response.getChannelCode())) {
            ChannelTransactionStatusResolution resolved = resolveWorldPay(
                    transactionType,
                    response.getChannelTradeStatus(),
                    response.getRawChannelStatus(),
                    response.getChannelResponseCode(),
                    response.getChannelResponseMessage());
            if (resolved.resolved()) {
                return resolved;
            }
        }
        return resolveGeneric(response.getChannelTradeStatus(),
                response.getRawChannelStatus(),
                response.getChannelResponseCode(),
                response.getChannelResponseMessage());
    }

    /**
     * 解析渠道回调或查询结果。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param callbackResult 渠道回调或查询结果
     * @return 平台状态解析结果
     */
    @Override
    public ChannelTransactionStatusResolution resolveCallback(String channelCode,
                                                              String transactionType,
                                                              ChannelCallbackResult callbackResult) {
        if (callbackResult == null || !StringUtils.hasText(callbackResult.getChannelTradeStatus())) {
            return unresolved(callbackResult);
        }
        if (isWorldPay(channelCode, callbackResult.getChannelCode())) {
            ChannelTransactionStatusResolution resolved = resolveWorldPay(
                    transactionType,
                    callbackResult.getChannelTradeStatus(),
                    callbackResult.getRawChannelStatus(),
                    callbackResult.getChannelResponseCode(),
                    callbackResult.getChannelResponseMessage());
            if (resolved.resolved()) {
                return resolved;
            }
        }
        return resolveGeneric(callbackResult.getChannelTradeStatus(),
                callbackResult.getRawChannelStatus(),
                callbackResult.getChannelResponseCode(),
                callbackResult.getChannelResponseMessage());
    }

    /**
     * 解析 WorldPay 渠道状态对应的平台状态。
     * <p>
     * WPGXML/WPGJSON 的同步和回调状态语义不同于普通渠道：AUTHORISED 对授权类动作是成功终态，
     * 但对一步支付、请款和预授权完成只是等待 CAPTURED/SETTLED 的中间态。
     *
     * @param transactionType 平台交易类型
     * @param channelTradeStatus 渠道统一状态
     * @param rawChannelStatus 渠道原始状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 平台状态解析结果
     */
    private ChannelTransactionStatusResolution resolveWorldPay(String transactionType,
                                                               String channelTradeStatus,
                                                               String rawChannelStatus,
                                                               String channelResponseCode,
                                                               String channelResponseMessage) {
        String normalizedStatus = normalize(firstText(rawChannelStatus, channelTradeStatus, channelResponseCode));
        if (WORLDPAY_FAILED_STATUSES.contains(normalizedStatus)
                || ChannelTradeStatus.FAILED.getCode().equals(normalize(channelTradeStatus))) {
            return failed(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (isWorldPayCaptureConfirmed(transactionType, normalizedStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (isWorldPayAuthorised(transactionType, normalizedStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        // WPGXML/WPGJSON 的 AUTHORISED 对一步支付、请款和预授权完成只表示授权层成功，不能提前标记平台资金成功。
        if (WORLDPAY_AUTHORISED_STATUSES.contains(normalizedStatus)) {
            return waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (WORLDPAY_PENDING_STATUSES.contains(normalizedStatus)
                || ChannelTradeStatus.PENDING.getCode().equals(normalize(channelTradeStatus))
                || ChannelTradeStatus.PROCESSING.getCode().equals(normalize(channelTradeStatus))) {
            return waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        return unresolved(rawChannelStatus, channelResponseCode, channelResponseMessage);
    }

    /**
     * 判断 WorldPay 原始状态是否已经确认资金动作成功。
     * <p>
     * 对一步支付、请款和预授权完成，只有 CAPTURED/SETTLED 这类资金确认状态才允许推进平台成功；
     * REFUND 则按退款成功事件单独判断，避免把 AUTHORISED 误当成资金终态。
     *
     * @param transactionType 平台交易类型
     * @param normalizedStatus 归一化后的 WorldPay 原始状态
     * @return true 表示可映射为平台成功终态
     */
    private boolean isWorldPayCaptureConfirmed(String transactionType, String normalizedStatus) {
        if (WORLDPAY_CAPTURED_STATUSES.contains(normalizedStatus)) {
            return true;
        }
        return PaymentTransactionTypeEnum.REFUND.getCode().equals(transactionType)
                && WORLDPAY_REFUND_SUCCESS_STATUSES.contains(normalizedStatus);
    }

    /**
     * 判断 WorldPay 授权类同步结果是否可直接视为平台成功。
     * <p>
     * 只有纯授权、预授权和增量授权的 AUTHORISED 表示授权动作成功；一步支付、请款和预授权完成仍要等待
     * CAPTURED/SETTLED 回调或查询勾兑确认。
     *
     * @param transactionType 平台交易类型
     * @param normalizedStatus 归一化后的 WorldPay 原始状态
     * @return true 表示授权类动作可推进成功
     */
    private boolean isWorldPayAuthorised(String transactionType, String normalizedStatus) {
        return WORLDPAY_AUTHORISED_STATUSES.contains(normalizedStatus)
                && (PaymentTransactionTypeEnum.AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode().equals(transactionType)
                || PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType));
    }

    /**
     * 按通用渠道状态映射平台交易状态。
     * <p>
     * 非 WorldPay 渠道沿用渠道统一状态：SUCCESS/FAILED 进入终态，NEED_REDIRECT/PENDING 保持待处理，
     * 其他状态按处理中处理。
     *
     * @param channelTradeStatus 渠道统一状态
     * @param rawChannelStatus 渠道原始状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 平台状态解析结果
     */
    private ChannelTransactionStatusResolution resolveGeneric(String channelTradeStatus,
                                                              String rawChannelStatus,
                                                              String channelResponseCode,
                                                              String channelResponseMessage) {
        if (ChannelTradeStatus.SUCCESS.getCode().equals(channelTradeStatus)) {
            return success(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.FAILED.getCode().equals(channelTradeStatus)) {
            return failed(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.NEED_REDIRECT.getCode().equals(channelTradeStatus)) {
            return needRedirect(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        if (ChannelTradeStatus.PENDING.getCode().equals(channelTradeStatus)) {
            return waitingCallback(rawChannelStatus, channelResponseCode, channelResponseMessage);
        }
        return processing(rawChannelStatus, channelResponseCode, channelResponseMessage);
    }

    /**
     * 判断当前渠道是否属于 WorldPay XML 或 JSON 独立渠道。
     *
     * @param channelCodes 候选渠道编码
     * @return true 表示命中 WPGXML 或 WPGJSON
     */
    private boolean isWorldPay(String... channelCodes) {
        if (channelCodes == null) {
            return false;
        }
        for (String channelCode : channelCodes) {
            if (WORLDPAY_CHANNELS.contains(normalize(channelCode))) {
                return true;
            }
        }
        return false;
    }

/**
 * 完成 success 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution success(String channelStatus,
                                                       String channelResponseCode,
                                                       String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        return resolution;
    }

/**
 * 完成 failed 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution failed(String channelStatus,
                                                      String channelResponseCode,
                                                      String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resolution.setFailReasonCode(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
        resolution.setFailReasonMessage(firstText(channelResponseMessage, "channel declined transaction"));
        return resolution;
    }

/**
 * 完成 need Redirect 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution needRedirect(String channelStatus,
                                                            String channelResponseCode,
                                                            String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PENDING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.WAITING_3DS.getCode());
        resolution.setPendingReasonCode(PaymentPendingReasonEnum.NEED_REDIRECT.getCode());
        return resolution;
    }

/**
 * 完成 waiting Callback 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution waitingCallback(String channelStatus,
                                                               String channelResponseCode,
                                                               String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PENDING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        resolution.setPendingReasonCode(PaymentPendingReasonEnum.WAITING_CHANNEL_CALLBACK.getCode());
        return resolution;
    }

    /**
     * 完成 processing 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ChannelTransactionStatusResolution processing() {
        return processing(null, null, null);
    }

/**
 * 完成 processing 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution processing(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = base(channelStatus, channelResponseCode, channelResponseMessage);
        resolution.setTargetStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        return resolution;
    }

    /**
     * 完成 unresolved 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param callbackResult callback Result 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ChannelTransactionStatusResolution unresolved(ChannelCallbackResult callbackResult) {
        if (callbackResult == null) {
            return new ChannelTransactionStatusResolution();
        }
        return unresolved(callbackResult.getRawChannelStatus(),
                callbackResult.getChannelResponseCode(),
                callbackResult.getChannelResponseMessage());
    }

/**
 * 完成 unresolved 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution unresolved(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        return base(channelStatus, channelResponseCode, channelResponseMessage);
    }

/**
 * 完成 base 的本地校验、字段转换或结果组装，供当前调用链继续使用。
 * <p>
 * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param channelResponseCode channel Response Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param channelResponseMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
    private ChannelTransactionStatusResolution base(String channelStatus,
                                                    String channelResponseCode,
                                                    String channelResponseMessage) {
        ChannelTransactionStatusResolution resolution = new ChannelTransactionStatusResolution();
        resolution.setChannelStatus(channelStatus);
        resolution.setChannelResponseCode(channelResponseCode);
        resolution.setChannelResponseMessage(channelResponseMessage);
        return resolution;
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /**
     * 完成 first Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultChannelTransactionStatusResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}

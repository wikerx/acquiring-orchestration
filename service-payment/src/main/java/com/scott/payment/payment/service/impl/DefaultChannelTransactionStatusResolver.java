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
 * 规范化success，返回当前业务步骤需要的业务值。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 规范化failed，返回当前业务步骤需要的业务值。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 规范化needredirect，返回当前业务步骤需要的业务值。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 整理waiting回调，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 处理processing流程，串联校验、状态判断和后续业务动作。
     * <p>
     * 前置条件：调用方已把 支付核心服务 的请求、消息或任务参数解析为当前方法可识别的模型。
     * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
     * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ChannelTransactionStatusResolution processing() {
        return processing(null, null, null);
    }

/**
 * 处理processing流程，串联校验、状态判断和后续业务动作。
 * <p>
 * 前置条件：调用方已把 支付核心服务 的请求、消息或任务参数解析为当前方法可识别的模型。
 * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
 * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化unresolved，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param callbackResult callback Result 输入值，参与 回调结果 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 规范化unresolved，返回当前业务步骤需要的业务值。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private ChannelTransactionStatusResolution unresolved(String channelStatus,
                                                          String channelResponseCode,
                                                          String channelResponseMessage) {
        return base(channelStatus, channelResponseCode, channelResponseMessage);
    }

/**
 * 整理基础，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param channelResponseCode channel Response Code 输入值，参与 渠道响应编码 的查询、校验、转换、写入或日志摘要
 * @param channelResponseMessage channel Response Message 输入值，参与 渠道响应说明 的查询、校验、转换、写入或日志摘要
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /**
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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

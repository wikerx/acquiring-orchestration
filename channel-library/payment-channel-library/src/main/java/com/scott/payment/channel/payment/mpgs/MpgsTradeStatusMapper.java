package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsTradeStatusMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 交易状态映射器，位于 payment-channel-library 渠道实现层，负责按 result 和收单响应码判断渠道交易结果，不处理平台交易状态机。
 * @status : create
 */
public class MpgsTradeStatusMapper {

    /**
     * RESULT SUCCESS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * RESULT PENDING 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RESULT_PENDING = "PENDING";

    /**
     * RESULT UNKNOWN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RESULT_UNKNOWN = "UNKNOWN";

    /**
     * APPROVED ACQUIRER CODE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String APPROVED_ACQUIRER_CODE = "00";

    /**
     * 将 MPGS 响应映射为渠道统一状态。
     * <p>
     * MPGS 顶层 result=SUCCESS 只表示网关请求成功受理；授权、支付、请款、退款等交易是否真正成功，
     * 必须以 response.acquirerCode=00 作为核心判断依据，避免把渠道拒付误判为平台成功。
     *
     * @param response MPGS 原始响应
     * @return 渠道统一状态编码
     */
    public String map(MpgsResponsePayload response) {
        if (response == null) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        String result = response.getResult();
        if (!StringUtils.hasText(result)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        if (RESULT_SUCCESS.equalsIgnoreCase(result)) {
            return APPROVED_ACQUIRER_CODE.equals(acquirerCode(response))
                    ? ChannelTradeStatus.SUCCESS.getCode()
                    : ChannelTradeStatus.FAILED.getCode();
        }
        if (RESULT_PENDING.equalsIgnoreCase(result)) {
            return ChannelTradeStatus.PENDING.getCode();
        }
        if (RESULT_UNKNOWN.equalsIgnoreCase(result)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        return ChannelTradeStatus.FAILED.getCode();
    }

    /**
     * 定义 acquirer Code 数据访问或对象转换入口，返回调用方需要的持久化记录或映射结果。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsTradeStatusMapper 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param response response 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String acquirerCode(MpgsResponsePayload response) {
        return response.getResponse() == null ? null : response.getResponse().getAcquirerCode();
    }
}

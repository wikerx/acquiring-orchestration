package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsTradeStatusMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 交易状态映射器，位于 payment-channel-mpgs 渠道实现层，负责按 result 和收单响应码判断渠道交易结果，不处理平台交易状态机。
 * @status : create
 */
public class MpgsTradeStatusMapper {

    /**
     * RESULT SUCCESS，用于保存 Mpgs Trade Status Mapper 中与 resultsuccess 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * RESULT PENDING，用于保存 Mpgs Trade Status Mapper 中与 resultpending 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RESULT_PENDING = "PENDING";

    /**
     * RESULT UNKNOWN，用于保存 Mpgs Trade Status Mapper 中与 resultunknown 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RESULT_UNKNOWN = "UNKNOWN";

    /**
     * APPROVED ACQUIRER CODE，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 整理收单机构结果码，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String acquirerCode(MpgsResponsePayload response) {
        return response.getResponse() == null ? null : response.getResponse().getAcquirerCode();
    }
}

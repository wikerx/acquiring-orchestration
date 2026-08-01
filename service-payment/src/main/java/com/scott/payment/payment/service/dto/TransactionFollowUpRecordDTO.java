package com.scott.payment.payment.service.dto;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.entity.TransactionOrderDO;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionFollowUpRecordDTO
 * @date : 2026-07-14 19:40
 * @email : scott_x@163.com
 * @description : 后续交易动作记录 DTO，位于 service-payment 服务 DTO 层，承载原交易、路由、渠道响应和交易结果，供交易事实记录服务写入动作单和状态历史。
 * @status : create
 */
@Data
public class TransactionFollowUpRecordDTO implements Serializable {

    /**
     * 序列化版本号，用于服务内测试和后续补偿场景对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 原交易生命周期主单，必须来自 transaction_date_time + transaction_id 精确定位。
     */
    private TransactionOrderDO sourceOrderDO;

    /**
     * 后续动作内部命令，包含本次动作号、金额、币种和原交易引用。
     */
    private PaymentCreateCommandDTO commandDTO;

    /**
     * 后续动作路由结果，渠道调用前生成。
     */
    private PaymentRouteResultDTO routeResultDTO;

    /**
     * 渠道同步响应；渠道超时或未同步返回时可为空。
     */
    private ChannelPaymentResponse channelResponse;

    /**
     * 渠道调用完整结果，包含请求对象、同步响应和耗时，用于渠道请求与交互日志落库。
     */
    private PaymentChannelInvokeResultDTO channelInvokeResultDTO;

    /**
     * 后续动作返回结果。
     */
    private PaymentCreateResultDTO resultDTO;

    /**
     * 后续动作风控策略结果；当前后续交易不适用内风控，默认由记录服务按 SKIP 写入审计事件。
     */
    private PaymentRiskDecisionEnum riskDecisionEnum;

    /**
     * 交易币种默认小数位，来自 ISO 字典。
     */
    private int currencyExponent;
}

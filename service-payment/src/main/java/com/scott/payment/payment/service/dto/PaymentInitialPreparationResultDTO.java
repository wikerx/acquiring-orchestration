package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInitialPreparationResultDTO
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 首次 Payment 本地准备结果 DTO，位于 service-payment 服务 DTO 层，用于把已提交的本地事实传递给事务外渠道调用。
 * @status : create
 */
@Data
public class PaymentInitialPreparationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否需要在准备事务提交后继续调用渠道。
     */
    private boolean callChannel;

    /**
     * 是否命中重复请求并直接返回原交易。
     */
    private boolean duplicate;

    /**
     * 首次交易幂等键。
     */
    private String idempotencyKey;

    /**
     * 已规范化并可用于渠道调用的命令对象。
     */
    private PaymentCreateCommandDTO commandDTO;

    /**
     * 渠道路由结果；风控短路或路由前失败时可为空。
     */
    private PaymentRouteResultDTO routeResultDTO;

    /**
     * 渠道调用前已持久化的请求身份；不调用渠道时为空。
     */
    private PaymentPreparedChannelRequestDTO preparedChannelRequestDTO;

    /**
     * 准备阶段返回给商户或后续结果处理的交易结果。
     */
    private PaymentCreateResultDTO resultDTO;

    /**
     * 本地准备阶段使用的风控决策。
     */
    private PaymentRiskDecisionEnum riskDecisionEnum;

    /**
     * 交易币种默认辅币位。
     */
    private int currencyExponent;

    /**
     * 构造重复请求准备结果。
     *
     * @param resultDTO 原交易结果
     * @return 准备结果
     */
    public static PaymentInitialPreparationResultDTO duplicate(PaymentCreateResultDTO resultDTO) {
        PaymentInitialPreparationResultDTO target = new PaymentInitialPreparationResultDTO();
        target.setDuplicate(true);
        target.setCallChannel(false);
        target.setResultDTO(resultDTO);
        return target;
    }
}

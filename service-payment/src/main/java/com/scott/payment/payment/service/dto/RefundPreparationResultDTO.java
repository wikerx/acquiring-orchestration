package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import lombok.Data;

import java.io.Serializable;

/**
 * Refund 本地准备结果 DTO，承载已提交的退款动作事实、幂等结果和渠道请求身份。
 */
@Data
public class RefundPreparationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否需要调用渠道；幂等命中、准备失败或本地终态结果均为 false。
     */
    private boolean callChannel;

    /**
     * 是否命中既有幂等结果；为 true 时必须复用原结果且禁止重复调用渠道。
     */
    private boolean duplicate;

    /**
     * 资金类请求幂等键，用于在同一商户和交易动作范围内识别重复提交。
     */
    private String idempotencyKey;

    /**
     * 完成本地准备和字段归一后的支付命令，供渠道调用阶段使用。
     */
    private PaymentCreateCommandDTO commandDTO;

    /**
     * 后续交易关联的原交易主单快照，用于校验可操作状态、剩余金额和原渠道身份。
     */
    private TransactionOrderDO sourceOrderDO;

    /**
     * 本次交易锁定的渠道路由结果，后续渠道调用不得重新选择路由。
     */
    private PaymentRouteResultDTO routeResultDTO;

    /**
     * 已完成金额、币种和渠道身份归一的渠道请求，仅用于本次渠道调用。
     */
    private PaymentPreparedChannelRequestDTO preparedChannelRequestDTO;

    /**
     * 无需调用渠道时直接返回的支付结果，例如幂等命中或准备阶段拒绝。
     */
    private PaymentCreateResultDTO resultDTO;

    /**
     * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
     */
    private int currencyExponent;

    /**
     * 创建命中幂等结果的准备结果，明确禁止再次调用渠道。
     * @param resultDTO 已持久化的原退款结果快照
     * @return 禁止再次占用额度或调用渠道的幂等命中结果
     */
    public static RefundPreparationResultDTO duplicate(PaymentCreateResultDTO resultDTO) {
        RefundPreparationResultDTO target = new RefundPreparationResultDTO();
        target.setDuplicate(true);
        target.setCallChannel(false);
        target.setResultDTO(resultDTO);
        return target;
    }
}

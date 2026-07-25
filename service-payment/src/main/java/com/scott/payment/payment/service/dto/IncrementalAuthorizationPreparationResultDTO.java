package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IncrementalAuthorizationPreparationResultDTO
 * @date : 2026-07-24 00:00
 * @description : Incremental Authorization 本地准备结果 DTO，承载已提交的动作事实、幂等结果和渠道请求身份。
 * @status : create
 */
@Data
public class IncrementalAuthorizationPreparationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean callChannel;

    private boolean duplicate;

    private String idempotencyKey;

    private PaymentCreateCommandDTO commandDTO;

    private TransactionOrderDO sourceOrderDO;

    private PaymentRouteResultDTO routeResultDTO;

    private PaymentPreparedChannelRequestDTO preparedChannelRequestDTO;

    private PaymentCreateResultDTO resultDTO;

    private int currencyExponent;

    public static IncrementalAuthorizationPreparationResultDTO duplicate(PaymentCreateResultDTO resultDTO) {
        IncrementalAuthorizationPreparationResultDTO target = new IncrementalAuthorizationPreparationResultDTO();
        target.setDuplicate(true);
        target.setCallChannel(false);
        target.setResultDTO(resultDTO);
        return target;
    }
}

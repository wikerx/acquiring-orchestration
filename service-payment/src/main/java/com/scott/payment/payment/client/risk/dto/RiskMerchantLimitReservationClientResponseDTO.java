package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskMerchantLimitReservationClientResponseDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : service-risk 商户累计限额预占补偿结果。
 * @status : create
 */
@Data
public class RiskMerchantLimitReservationClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次实际完成回滚的预占记录数量。 */
    private int applied;

    /** 已处于目标状态、按幂等成功处理的记录数量。 */
    private int idempotent;

    /** 因状态或归属冲突而未回滚的记录数量。 */
    private int conflicted;
}

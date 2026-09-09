package com.scott.payment.risk.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationCommandResultDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占内部生命周期命令结果。
 * @status : create
 */
@Data
public class MerchantLimitReservationCommandResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 本次成功发生状态迁移的预占记录数。
     */
    private int applied;

    /**
     * 已处于目标或等价终态、按幂等成功处理的记录数。
     */
    private int idempotent;

    /**
     * 因状态冲突、Redis 回滚失败或并发版本变化而未迁移的记录数。
     */
    private int conflicted;
}

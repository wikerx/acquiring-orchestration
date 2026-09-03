package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalDailySequenceDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 冲正申请单号数据库日序列锁读模型；依赖行锁和 version CAS 分配当日唯一序号。
 * @status : create
 */
@Data
public class SettlementReversalDailySequenceDO {
    /** UTC 业务日期，也是日序列表主键。 */
    private LocalDate businessDate;
    /** 当日已分配最大序号，范围不得超过八位。 */
    private Integer currentSequence;
    /** 日序列递增 CAS 版本。 */
    private Long version;
}

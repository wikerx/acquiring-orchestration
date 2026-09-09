package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchDailySequenceDO
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次数据库日序列实体；必须在主库事务内锁行分配，序号允许空洞但禁止回收。
 * @status : create
 */
@Data
@TableName("settlement_batch_daily_sequence")
public class SettlementBatchDailySequenceDO {
    /** 独立结算业务日期，数据库主键。 */
    private LocalDate businessDate;
    /** 当日已经分配的最大序号。 */
    private Integer currentSequence;
    /** 序列审计和 CAS 版本。 */
    private Long version;
    /** 首次创建时间。 */
    private LocalDateTime createTime;
    /** 最近更新时间。 */
    private LocalDateTime updateTime;
}

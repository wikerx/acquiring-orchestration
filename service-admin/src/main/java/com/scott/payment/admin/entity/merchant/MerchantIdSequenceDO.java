package com.scott.payment.admin.entity.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIdSequenceDO
 * @date : 2026-09-17 18:10
 * @email : scott_x@163.com
 * @description : 商户号年度数据库序列实体；每个年份独占一行，并在主库事务内加锁分配四位年度流水。
 * @status : create
 */
@Data
@TableName("base_merchant_id_sequence")
public class MerchantIdSequenceDO {

    /** 两位业务年份，格式为 yy；数据库主键；非敏感字段；不允许为空。 */
    @TableId(value = "business_year", type = IdType.INPUT)
    private String businessYear;

    /** 当前年份已经分配的最大流水；单位为个；范围为 0 至 9999；非敏感字段；不允许为空。 */
    private Integer currentSequence;

    /** CAS 版本号；单位为次；用于检测事务内的异常并发更新；非敏感字段；不允许为空。 */
    private Long version;

    /** 序列记录首次创建时间；格式为 DATETIME(3)；非敏感字段；不允许为空。 */
    private LocalDateTime gmtCreate;

    /** 序列记录最近更新时间；格式为 DATETIME(3)；非敏感字段；不允许为空。 */
    private LocalDateTime gmtModified;
}

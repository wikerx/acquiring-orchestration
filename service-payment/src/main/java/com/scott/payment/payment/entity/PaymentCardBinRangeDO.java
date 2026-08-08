package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付服务只读卡 BIN 区间实体。 */
@Data
@TableName("base_card_bin_range")
public class PaymentCardBinRangeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cardBinStart;
    private Long cardBinEnd;
    private Integer binLength;
    private String cardBrand;
    private Integer sourcePriority;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private Integer status;
    private LocalDateTime updateTime;
    private Long deleted;
}

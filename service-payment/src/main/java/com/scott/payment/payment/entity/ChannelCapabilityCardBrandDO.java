package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 交易侧渠道能力卡品牌只读实体。 */
@Data
@TableName("channel_capability_card_brand")
public class ChannelCapabilityCardBrandDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long capabilityId;

    private Long channelId;

    private String channelCode;

    private String cardBrand;

    private Integer brandStatus;

    private Integer sortOrder;

    private Long deleted;
}

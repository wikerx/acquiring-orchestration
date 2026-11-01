package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 交易侧渠道能力卡品牌只读实体。
 */
@Data
@TableName("channel_capability_card_brand")
public class ChannelCapabilityCardBrandDO {


    @TableId(type = IdType.AUTO)
    private Long id;


    private Long capabilityId;


    private Long channelId;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;

    /**
     * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
     */
    private String cardBrand;


    private Integer brandStatus;

    /**
     * {@code sortOrder}，用于控制列表展示或规则匹配时的排序优先级。
     */
    private Integer sortOrder;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}

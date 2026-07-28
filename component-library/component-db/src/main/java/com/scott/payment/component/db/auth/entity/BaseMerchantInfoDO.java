package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMerchantInfoDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户基础信息轻量实体，用于商户系统账号绑定校验
 * @status : create
 */
@Data
@TableName("base_merchant_info")
public class BaseMerchantInfoDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户主体名称。
     */
    private String merchantName;

    /**
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称。
     */
    private String billingDescriptor;

    /**
     * 商户简称。
     */
    private String merchantShortName;

    /**
     * 商户状态。1 表示正常，2 表示冻结，3 表示关闭。
     */
    private Integer merchantStatus;

    /**
     * 商户类别码 MCC。
     */
    private String merchantCategoryCode;

    /**
     * 国家三字码。
     */
    private String countryCode;

    /**
     * 区域代码。
     */
    private String regionCode;

    /**
     * 城市。
     */
    private String city;

    /**
     * 地址。
     */
    private String addressLine;

    /**
     * 商户经营地址邮编。
     */
    private String postalCode;

    /**
     * 商户联系人姓名。
     */
    private String contactName;

    /**
     * 联系邮箱。
     */
    private String contactEmail;

    /**
     * 联系电话。
     */
    private String contactPhone;

    /**
     * 默认结算币种。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。
     */
    private String timezone;

    /**
     * 风险等级：1 低，2 中，3 高。
     */
    private Integer riskLevel;

    /**
     * 创建时间。
     */
    private LocalDateTime gmtCreate;

    /**
     * 修改时间。
     */
    private LocalDateTime gmtModified;

    /**
     * 删除标识。
     */
    private Integer deleted;
}

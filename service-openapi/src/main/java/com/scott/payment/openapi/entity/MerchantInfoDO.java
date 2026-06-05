package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scott.payment.component.db.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantInfoDO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户基础信息数据库实体
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_merchant_info")
public class MerchantInfoDO extends BaseEntity {

    /**
     * 序列化版本号，用于保证实体在缓存、测试或序列化传输场景中的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付框架颁发的商户号，是 OpenAPI 鉴权、密钥查询和交易归属判断的核心业务主键。
     */
    private String merchantId;

    /**
     * 商户主体名称，建议与商户开户注册名称保持一致。
     */
    private String merchantName;

    /**
     * 商户简称，用于管理后台列表、运营查询和日志摘要展示。
     */
    private String merchantShortName;

    /**
     * 商户状态。1 表示正常可交易，2 表示冻结，3 表示关闭。
     */
    private Integer merchantStatus;

    /**
     * 商户类别码，外卡收单通常使用 MCC 四位数字识别商户经营类型。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码，使用 ISO 3166-1 alpha-3，例如 USA、CAN、GBR。
     */
    private String countryCode;

    /**
     * 商户所在州、省或区域代码，美国、加拿大等国家建议使用标准州代码。
     */
    private String regionCode;

    /**
     * 商户所在城市，建议保存英文或渠道要求的标准城市名称。
     */
    private String city;

    /**
     * 商户开户地址或经营地址，用于卡组织资料、渠道风控和人工审核。
     */
    private String addressLine;

    /**
     * 商户联系人邮箱，用于开户通知、密钥交付通知和异常沟通。
     */
    private String contactEmail;

    /**
     * 商户联系人电话，用于人工审核、风控回访和异常交易联系。
     */
    private String contactPhone;

    /**
     * 默认结算币种，使用 ISO 4217 三字码，例如 USD、EUR、CNY。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。数据库交易时间统一使用 UTC+8，该字段用于商户展示和对账口径转换。
     */
    private String timezone;

    /**
     * 商户风险等级。1 表示低风险，2 表示普通风险，3 表示高风险。
     */
    private Integer riskLevel;

    /**
     * 逻辑删除标识。0 表示正常，1 表示删除，查询时必须过滤已删除记录。
     */
    private Integer deleted;
}

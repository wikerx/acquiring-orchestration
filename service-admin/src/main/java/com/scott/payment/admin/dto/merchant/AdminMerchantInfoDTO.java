package com.scott.payment.admin.dto.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoDTO
 * @date : 2026-06-19 22:05
 * @email : scott_x@163.com
 * @description : 管理后台商户基础信息响应 DTO
 * @status : create
 *
 * <p>用于管理后台商户详情与列表展示，承载商户基础档案以及当前生效的密钥摘要信息。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Info 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AdminMerchantInfoDTO {

    /**
     * 商户资料主键 ID。
     *
     * <p>后台主键是雪花 Long，超过 JavaScript 安全整数范围；接口返回字符串，避免前端编辑时 ID 被精度截断。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 商户号，作为外部接入与后台检索的稳定业务标识。
     */
    private String merchantId;

    /**
     * 商户主体名称，必须使用英文、数字、空格及常见英文符号。
     */
    private String merchantName;

    /**
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称。
     */
    private String billingDescriptor;

    /**
     * 商户简称，可为空。
     */
    private String merchantShortName;

    /**
     * 商户状态，通常用于区分启用、停用等业务状态。
     */
    private Integer merchantStatus;

    /**
     * 商户 MCC 类目编码。
     */
    private String merchantCategoryCode;

    /**
     * 商户归属国家代码，通常为 ISO 标准编码。
     */
    private String countryCode;

    /**
     * 商户归属地区编码，可为空。
     */
    private String regionCode;

    /**
     * 商户所在城市，可为空。
     */
    private String city;

    /**
     * 商户详细地址，可为空。
     */
    private String addressLine;

    /**
     * 商户经营地址邮编，可为空。
     */
    private String postalCode;

    /**
     * 商户联系人姓名，可为空。
     */
    private String contactName;

    /**
     * 商户联系人邮箱，属于敏感联系信息，展示时应按需要脱敏。
     */
    private String contactEmail;

    /**
     * 商户联系人手机号，属于敏感联系信息，展示时应按需要脱敏。
     */
    private String contactPhone;

    /**
     * 商户结算币种代码。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区，例如 Asia/Shanghai。
     */
    private String timezone;

    /**
     * 商户风险等级，用于后台风险分层管理。
     */
    private Integer riskLevel;

    /**
     * 记录创建时间。
     */
    private LocalDateTime gmtCreate;

    /**
     * 最近更新时间。
     */
    private LocalDateTime gmtModified;

    /**
     * 当前生效的 JWT 密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO jwtKey;

    /**
     * 当前生效的平台请求体密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO platformPayloadKey;

    /**
     * 当前生效的商户响应密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO responseKey;
}

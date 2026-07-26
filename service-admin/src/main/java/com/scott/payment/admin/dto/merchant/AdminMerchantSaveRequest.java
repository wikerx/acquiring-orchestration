package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantSaveRequest
 * @date : 2026-06-19 22:07
 * @email : scott_x@163.com
 * @description : 管理后台商户基础信息保存请求 DTO
 * @status : create
 *
 * <p>用于新增或更新商户基础档案，承载商户识别信息、联系信息、地区信息和结算信息。</p>
 */
@Data
public class AdminMerchantSaveRequest {

    /**
     * 商户号，作为商户稳定业务标识；新增时由服务端生成，编辑时用于校验不可变更。
     */
    private String merchantId;

    /**
     * 商户主体名称，必须使用英文、数字、空格及常见英文符号，禁止中文。
     */
    @NotBlank(message = "商户名称不能为空")
    @Pattern(regexp = "^[\\x20-\\x7E]{1,128}$", message = "商户名称仅支持英文、数字、空格及常见英文符号")
    private String merchantName;

    /**
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称，禁止中文。
     */
    @NotBlank(message = "账单描述不能为空")
    @Pattern(regexp = "^[\\x20-\\x7E]{1,64}$", message = "账单描述仅支持英文、数字、空格及常见英文符号")
    private String billingDescriptor;

    /**
     * 商户简称。
     */
    @NotBlank(message = "商户简称不能为空")
    private String merchantShortName;

    /**
     * 商户 MCC 类目编码。
     */
    @NotBlank(message = "MCC不能为空")
    private String merchantCategoryCode;

    /**
     * 商户归属国家代码。
     */
    @NotBlank(message = "国家代码不能为空")
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
     * 联系人邮箱，属于敏感联系信息。
     */
    @NotBlank(message = "联系邮箱不能为空")
    @Email(message = "联系邮箱格式不正确")
    private String contactEmail;

    /**
     * 联系人手机号，属于敏感联系信息，可为空。
     */
    private String contactPhone;

    /**
     * 结算币种代码。
     */
    @NotBlank(message = "结算币种不能为空")
    private String settlementCurrency;

    /**
     * 商户业务时区，例如 Asia/Shanghai。
     */
    @NotBlank(message = "时区不能为空")
    private String timezone;

    /**
     * 商户状态。
     */
    @NotNull(message = "商户状态不能为空")
    private Integer merchantStatus;

    /**
     * 商户风险等级，可为空；为空时由服务端填充默认值。
     */
    private Integer riskLevel;
}

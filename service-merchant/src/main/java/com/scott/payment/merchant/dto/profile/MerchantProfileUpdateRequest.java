package com.scott.payment.merchant.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileUpdateRequest
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户门户低风险资料即时更新请求，只承载展示偏好和联系人字段
 * @status : create
 */
@Data
public class MerchantProfileUpdateRequest {

    /** 商户简称，最多 64 个字符，不允许为空。 */
    @NotBlank(message = "商户简称不能为空")
    @Size(max = 64, message = "商户简称不能超过64个字符")
    private String merchantShortName;

    /** 商户联系人姓名，最多 128 个字符，属于可识别资料。 */
    @Size(max = 128, message = "联系人姓名不能超过128个字符")
    private String contactName;

    /** 主要联系人职位，最多 64 个字符。 */
    @Size(max = 64, message = "联系人职位不能超过64个字符")
    private String contactTitle;

    /** 联系邮箱，属于敏感联系资料，不允许为空且不会写入普通 Redis 缓存。 */
    @NotBlank(message = "联系邮箱不能为空")
    @Email(message = "联系邮箱格式不正确")
    @Size(max = 128, message = "联系邮箱不能超过128个字符")
    private String contactEmail;

    /** 联系电话，最多 32 个字符，属于敏感联系资料。 */
    @Size(max = 32, message = "联系电话不能超过32个字符")
    @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "联系电话必须是包含国家代码的完整国际号码")
    private String contactPhone;

    /** 备用联系邮箱，可为空。 */
    @Email(message = "备用联系邮箱格式不正确")
    @Size(max = 128, message = "备用联系邮箱不能超过128个字符")
    private String alternateEmail;

    /** 财务联系人姓名，可为空。 */
    @Size(max = 128, message = "财务联系人姓名不能超过128个字符")
    private String financeContactName;

    /** 财务联系人邮箱，可为空。 */
    @Email(message = "财务联系人邮箱格式不正确")
    @Size(max = 128, message = "财务联系人邮箱不能超过128个字符")
    private String financeContactEmail;

    /** 技术联系人姓名，可为空。 */
    @Size(max = 128, message = "技术联系人姓名不能超过128个字符")
    private String technicalContactName;

    /** 技术联系人邮箱，可为空。 */
    @Email(message = "技术联系人邮箱格式不正确")
    @Size(max = 128, message = "技术联系人邮箱不能超过128个字符")
    private String technicalContactEmail;

    /** 商户门户和通知默认语言，只允许 zh-CN 或 en-US。 */
    @NotBlank(message = "默认语言不能为空")
    @Pattern(regexp = "^(zh-CN|en-US)$", message = "默认语言仅支持 zh-CN 或 en-US")
    private String defaultLocale;

    /** IANA 时区名称，例如 Asia/Shanghai，不允许为空。 */
    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过64个字符")
    private String timezone;
}

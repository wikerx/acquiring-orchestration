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
 * @description : 商户门户主体资料自助更新请求，只承载允许商户维护的展示、经营地址和联系人字段
 * @status : create
 */
@Data
public class MerchantProfileUpdateRequest {

    /** 账单描述，1 至 64 个可打印 ASCII 字符，不允许为空。 */
    @NotBlank(message = "账单描述不能为空")
    @Pattern(regexp = "^[\\x20-\\x7E]{1,64}$", message = "账单描述仅支持英文、数字、空格及常见英文符号")
    private String billingDescriptor;

    /** 商户简称，最多 64 个字符，不允许为空。 */
    @NotBlank(message = "商户简称不能为空")
    @Size(max = 64, message = "商户简称不能超过64个字符")
    private String merchantShortName;

    /** 商户经营区域代码，最多 64 个字符，允许为空。 */
    @Size(max = 64, message = "区域代码不能超过64个字符")
    private String regionCode;

    /** 商户经营城市，最多 128 个字符，允许为空。 */
    @Size(max = 128, message = "城市不能超过128个字符")
    private String city;

    /** 商户详细经营地址，最多 255 个字符，属于敏感资料且不会写入普通 Redis 缓存。 */
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String addressLine;

    /** 商户经营地址邮编，最多 32 个字符，允许为空。 */
    @Size(max = 32, message = "邮编不能超过32个字符")
    private String postalCode;

    /** 商户联系人姓名，最多 128 个字符，属于可识别资料。 */
    @Size(max = 128, message = "联系人姓名不能超过128个字符")
    private String contactName;

    /** 联系邮箱，属于敏感联系资料，不允许为空且不会写入普通 Redis 缓存。 */
    @NotBlank(message = "联系邮箱不能为空")
    @Email(message = "联系邮箱格式不正确")
    @Size(max = 128, message = "联系邮箱不能超过128个字符")
    private String contactEmail;

    /** 联系电话，最多 32 个字符，属于敏感联系资料。 */
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    /** IANA 时区名称，例如 Asia/Shanghai，不允许为空。 */
    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过64个字符")
    private String timezone;
}

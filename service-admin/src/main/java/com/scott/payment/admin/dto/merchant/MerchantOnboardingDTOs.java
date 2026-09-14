package com.scott.payment.admin.dto.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOnboardingDTOs
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 管理端商户开户复合 DTO 集合，承载相关人员、审核决定、审核轨迹和合规资料元数据
 * @status : create
 */
public final class MerchantOnboardingDTOs {

    private MerchantOnboardingDTOs() {
    }

    /** 商户法定代表人、董事、UBO 或授权人；同一人员可同时承担多个角色。 */
    @Data
    public static class RelatedPerson {

        /** 人员记录主键；新增时允许为空，接口按字符串序列化避免 JavaScript 精度损失。 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        /** 人员法定姓名；提交审核前不允许为空，非敏感。 */
        @NotBlank(message = "人员姓名不能为空")
        private String fullName;

        /** 人员角色编码集合；可包含 LEGAL_REPRESENTATIVE、DIRECTOR、UBO 或 AUTHORIZED_SIGNER。 */
        private List<String> personRoles;

        /** 国籍 ISO 3166-1 alpha-3 代码；提交审核前不允许为空。 */
        private String nationality;

        /** 出生日期；个人敏感信息，提交审核前不允许为空。 */
        private LocalDate dateOfBirth;

        /** 居住国家 ISO 3166-1 alpha-3 代码；提交审核前不允许为空。 */
        private String residenceCountry;

        /** 居住地址；个人敏感信息，可按国家或风险要求采集。 */
        private String residentialAddress;

        /** 证件类型编码，例如 PASSPORT 或 NATIONAL_ID。 */
        private String idType;

        /** 新增或换证时提交的明文证件号；高度敏感，响应中始终返回 null。 */
        private String idNumber;

        /** 证件号脱敏值；只用于后台核对，不可用于身份校验。 */
        private String idNumberMasked;

        /** 证件有效期；长期有效或国家规则不要求时允许为空。 */
        private LocalDate idExpiryDate;

        /** UBO 持股比例，单位百分比，允许范围 0 至 100。 */
        @DecimalMin(value = "0", message = "持股比例不能小于0")
        @DecimalMax(value = "100", message = "持股比例不能大于100")
        private BigDecimal ownershipPercentage;

        /** 是否为实际控制人；可为空表示草稿阶段尚未回答。 */
        private Boolean controllerFlag;

        /** 是否为政治公众人物 PEP；提交审核前应明确回答。 */
        private Boolean pepFlag;

        /** 人员联系邮箱；个人敏感信息，可为空。 */
        @Email(message = "人员邮箱格式不正确")
        private String email;

        /** 人员联系电话；个人敏感信息，可为空。 */
        private String phone;
    }

    /** 审核动作请求；仅允许 PASS、SUPPLEMENT 或 REJECT。 */
    @Data
    public static class ReviewRequest {

        /** 审核决定编码；不允许为空。 */
        @NotBlank(message = "审核决定不能为空")
        private String decision;

        /** 审核意见；要求补件或驳回时不允许为空。 */
        private String comment;
    }

    /** 审核历史记录；只读返回，不允许通过商户保存接口修改。 */
    @Data
    public static class ReviewRecord {

        /** 审核记录主键；按字符串序列化避免 JavaScript 精度损失。 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        /** 审核动作编码，例如 SUBMIT、PASS、SUPPLEMENT、REJECT 或 ACTIVATE。 */
        private String reviewAction;

        /** 动作前审核状态。 */
        private String fromStatus;

        /** 动作后审核状态。 */
        private String toStatus;

        /** 审核意见或补件说明；通过和激活动作允许为空。 */
        private String reviewComment;

        /** 操作人显示名称；系统操作时为 system。 */
        private String operatorName;

        /** 审核记录创建时间，精度为毫秒。 */
        private LocalDateTime gmtCreate;
    }

    /** 商户合规资料元数据；不包含 Bucket、Object Key 或文件正文。 */
    @Data
    public static class Document {

        /** 资料主键；按字符串序列化避免 JavaScript 精度损失。 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        /** 资料类型编码，例如 BUSINESS_LICENSE、UBO_ID。 */
        private String documentType;

        /** 经路径和控制字符清洗后的原始文件名。 */
        private String originalFilename;

        /** 通过文件魔数识别的 MIME 类型。 */
        private String contentType;

        /** 文件大小，单位字节。 */
        private Long fileSize;

        /** 文件 SHA-256 十六进制摘要；非密钥，仅用于完整性校验。 */
        private String sha256;

        /** 资料状态；当前上传成功后为 UPLOADED。 */
        private String documentStatus;

        /** 资料元数据创建时间，精度为毫秒。 */
        private LocalDateTime gmtCreate;
    }
}

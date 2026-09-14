package com.scott.payment.admin.entity.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOnboardingEntities
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户开户独立生命周期实体集合，集中定义相关人员、资料元数据和审核审计记录 DO
 * @status : create
 */
public final class MerchantOnboardingEntities {

    private MerchantOnboardingEntities() {
    }

    /** 商户相关人员实体；证件号只允许以密文和脱敏值持久化。 */
    @Data
    @TableName("merchant_related_person")
    public static class MerchantRelatedPersonDO {

        /** 自增主键 ID，不允许业务侧赋值。 */
        @TableId(type = IdType.AUTO)
        private Long id;

        /** 平台商户号；稳定业务关联键，不允许为空。 */
        private String merchantId;

        /** 人员法定姓名；提交审核前不允许为空。 */
        private String fullName;

        /** 人员角色编码，多值使用逗号分隔。 */
        private String personRoles;

        /** 国籍 ISO 3166-1 alpha-3 代码。 */
        private String nationality;

        /** 出生日期；个人敏感信息。 */
        private LocalDate dateOfBirth;

        /** 居住国家 ISO 3166-1 alpha-3 代码。 */
        private String residenceCountry;

        /** 居住地址；个人敏感信息，可为空。 */
        private String residentialAddress;

        /** 证件类型编码，例如 PASSPORT。 */
        private String idType;

        /** AES-GCM 证件号密文；高度敏感，不允许直接返回前端或记录日志。 */
        private String idNumberCipher;

        /** 证件号脱敏值；仅用于管理端识别。 */
        private String idNumberMasked;

        /** 证件有效期；长期有效时允许为空。 */
        private LocalDate idExpiryDate;

        /** UBO 持股比例，单位百分比，允许范围 0 至 100。 */
        private BigDecimal ownershipPercentage;

        /** 实际控制人标识；1 是、0 否。 */
        private Integer controllerFlag;

        /** 政治公众人物 PEP 标识；1 是、0 否。 */
        private Integer pepFlag;

        /** 人员联系邮箱；个人敏感信息，可为空。 */
        private String email;

        /** 人员联系电话；个人敏感信息，可为空。 */
        private String phone;

        /** 页面展示顺序；从 0 开始。 */
        private Integer displayOrder;

        /** 记录创建时间，精度为毫秒。 */
        private LocalDateTime gmtCreate;

        /** 记录修改时间，精度为毫秒。 */
        private LocalDateTime gmtModified;

        /** 软删除标识；0 有效、1 删除。 */
        private Integer deleted;
    }

    /** 通用业务资料元数据实体；正文位于私有对象存储。 */
    @Data
    @TableName("biz_document")
    public static class BizDocumentDO {

        /** 自增主键 ID，不允许业务侧赋值。 */
        @TableId(type = IdType.AUTO)
        private Long id;

        /** 业务类型；商户 KYB 固定为 MERCHANT_KYB。 */
        private String bizType;

        /** 业务归属标识；商户资料使用平台商户号。 */
        private String bizId;

        /** 资料类型编码，例如 BUSINESS_LICENSE 或 UBO_ID。 */
        private String documentType;

        /** 经路径和控制字符清洗后的原始文件名。 */
        private String originalFilename;

        /** 通过文件魔数识别的 MIME 类型。 */
        private String contentType;

        /** 文件大小，单位字节。 */
        private Long fileSize;

        /** 文件 SHA-256 十六进制摘要，用于下载完整性校验。 */
        private String sha256;

        /** 存储供应商标识，例如 MINIO 或 AWS_S3。 */
        private String storageProvider;

        /** 私有 Bucket 名称；不允许通过外部接口返回。 */
        private String bucketName;

        /** 私有对象键；不允许通过外部接口返回或写入普通日志。 */
        private String objectKey;

        /** 资料状态；当前上传成功后为 UPLOADED。 */
        private String documentStatus;

        /** 上传操作人显示名称；允许为空。 */
        private String uploadedBy;

        /** 元数据创建时间，精度为毫秒。 */
        private LocalDateTime gmtCreate;

        /** 元数据修改时间，精度为毫秒。 */
        private LocalDateTime gmtModified;

        /** 软删除标识；0 有效、1 删除。 */
        private Integer deleted;
    }

    /** 商户审核不可变记录实体；仅追加，不允许覆盖历史。 */
    @Data
    @TableName("merchant_review_record")
    public static class MerchantReviewRecordDO {

        /** 自增主键 ID，不允许业务侧赋值。 */
        @TableId(type = IdType.AUTO)
        private Long id;

        /** 平台商户号；稳定业务关联键，不允许为空。 */
        private String merchantId;

        /** 审核动作编码，例如 SUBMIT、PASS、SUPPLEMENT、REJECT 或 ACTIVATE。 */
        private String reviewAction;

        /** 动作前审核状态；不允许为空。 */
        private String fromStatus;

        /** 动作后审核状态；不允许为空。 */
        private String toStatus;

        /** 审核意见或补件说明；通过和激活动作允许为空。 */
        private String reviewComment;

        /** 操作人账号 ID；系统操作时允许为空。 */
        private String operatorId;

        /** 操作人显示名称；系统操作时为 system。 */
        private String operatorName;

        /** 审核记录创建时间，精度为毫秒。 */
        private LocalDateTime gmtCreate;
    }
}

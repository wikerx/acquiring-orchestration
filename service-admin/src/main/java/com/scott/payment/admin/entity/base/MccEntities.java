package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCC 基础数据表实体集合。
 *
 * <p>仅用于管理后台 MCC 分类、编码和风险策略维护，避免把基础数据维护逻辑下沉到支付核心或公共组件。</p>
 */
public final class MccEntities {

    private MccEntities() {
    }

    /**
     * MCC 一级分类数据库实体。
     */
    @Data
    @TableName("base_mcc_level1")
    public static class BaseMccLevel1DO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String level1Code;
        @TableField("level1_name_cn")
        private String nameCn;
        @TableField("level1_name_en")
        private String nameEn;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * MCC 二级分类数据库实体。
     */
    @Data
    @TableName("base_mcc_level2")
    public static class BaseMccLevel2DO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long level1Id;
        private String level2Code;
        @TableField("level2_name_cn")
        private String nameCn;
        @TableField("level2_name_en")
        private String nameEn;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * MCC 编码数据库实体。
     *
     * <p>MCC Code 本身是叶子节点，直接通过 level1_id / level2_id 挂在二级分类下。</p>
     */
    @Data
    @TableName("base_mcc_code")
    public static class BaseMccCodeDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long level1Id;
        private Long level2Id;
        private String mccCode;
        @TableField("mcc_name_cn")
        private String nameCn;
        @TableField("mcc_name_en")
        private String nameEn;
        private String mccType;
        private String riskLevel;
        private String deliveryApplicability;
        private String source;
        private String versionNo;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * MCC 风险策略数据库实体。
     */
    @Data
    @TableName("base_mcc_risk_policy")
    public static class BaseMccRiskPolicyDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String mccCode;
        private String cardScheme;
        private String channelScope;
        private String channelCode;
        private String countryScope;
        private String countryCode;
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;
        private Integer policyStatus;
        private Integer priority;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long deleted;
    }
}

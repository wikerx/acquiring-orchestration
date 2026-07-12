package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccEntities
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Mcc 实体集合，位于 service-admin 的数据实体层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
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
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String level1Code;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("level1_name_cn")
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("level1_name_en")
        private String nameEn;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * MCC 二级分类数据库实体。
     */
    @Data
    @TableName("base_mcc_level2")
    public static class BaseMccLevel2DO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long level1Id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String level2Code;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("level2_name_cn")
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("level2_name_en")
        private String nameEn;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
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
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long level1Id;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long level2Id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("mcc_name_cn")
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("mcc_name_en")
        private String nameEn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccType;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String deliveryApplicability;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String source;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String versionNo;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime expireTime;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * MCC 风险策略数据库实体。
     */
    @Data
    @TableName("base_mcc_risk_policy")
    public static class BaseMccRiskPolicyDO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardScheme;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String countryCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowOnboarding;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowAcquiring;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer requireEnhancedReview;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer policyStatus;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer priority;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime expireTime;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }
}

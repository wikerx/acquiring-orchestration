package com.scott.payment.admin.dto.base;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccVO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Mcc 视图对象，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class MccVO {

    private MccVO() {
    }

    /**
     * MCC 树节点响应。
     */
    @Data
    public static class MccTreeNodeVO {
        /**
         * 基础数据敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String nodeKey;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String parentNodeKey;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer level;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String code;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameEn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String label;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccNameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccNameEn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccType;
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
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
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
        private List<MccTreeNodeVO> children = new ArrayList<>();
    }

    /**
     * MCC 编码详情响应。
     */
    @Data
    public static class MccCodeVO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
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
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String level1Name;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String level2Name;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
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
    }

    /**
     * MCC 风险策略响应。
     */
    @Data
    public static class MccRiskPolicyVO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccNameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccNameEn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardScheme;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardSchemeName;
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
        private String countryNameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryNameEn;
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
    }

    /**
     * MCC 概览响应。
     */
    @Data
    public static class MccOverviewVO {
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private long level1Count;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private long level2Count;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private long mccCodeCount;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private long enabledMccCodeCount;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private long riskPolicyCount;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private long highRiskPolicyCount;
    }

    /**
     * MCC 下拉选项响应。
     */
    @Data
    public static class MccOptionVO {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String code;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String label;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameEn;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long parentId;
    }
}

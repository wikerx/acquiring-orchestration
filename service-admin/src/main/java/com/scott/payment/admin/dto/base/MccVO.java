package com.scott.payment.admin.dto.base;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MCC 管理后台响应对象集合。
 */
public final class MccVO {

    private MccVO() {
    }

    /**
     * MCC 树节点响应。
     */
    @Data
    public static class MccTreeNodeVO {
        private String nodeKey;
        private String nodeType;
        private Long id;
        private String parentNodeKey;
        private Integer level;
        private String code;
        private String nameCn;
        private String nameEn;
        private String label;
        private String mccCode;
        private String mccNameCn;
        private String mccNameEn;
        private String riskLevel;
        private String mccType;
        private String deliveryApplicability;
        private String source;
        private String versionNo;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private Integer status;
        private Integer sortNo;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private List<MccTreeNodeVO> children = new ArrayList<>();
    }

    /**
     * MCC 编码详情响应。
     */
    @Data
    public static class MccCodeVO {
        private Long id;
        private Long level1Id;
        private Long level2Id;
        private String level1Name;
        private String level2Name;
        private String mccCode;
        private String nameCn;
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
    }

    /**
     * MCC 风险策略响应。
     */
    @Data
    public static class MccRiskPolicyVO {
        private Long id;
        private String mccCode;
        private String mccNameCn;
        private String mccNameEn;
        private String cardScheme;
        private String cardSchemeName;
        private String channelScope;
        private String channelCode;
        private String countryScope;
        private String countryCode;
        private String countryNameCn;
        private String countryNameEn;
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * MCC 概览响应。
     */
    @Data
    public static class MccOverviewVO {
        private long level1Count;
        private long level2Count;
        private long mccCodeCount;
        private long enabledMccCodeCount;
        private long riskPolicyCount;
        private long highRiskPolicyCount;
    }

    /**
     * MCC 下拉选项响应。
     */
    @Data
    public static class MccOptionVO {
        private Long id;
        private String code;
        private String label;
        private String nameCn;
        private String nameEn;
        private String nodeType;
        private Long parentId;
    }
}

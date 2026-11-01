package com.scott.payment.admin.dto.base;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MCC 管理响应模型集合，只向运营后台展示必要业务事实，不暴露持久化实体。
 */
public final class MccVO {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private MccVO() {
    }

    /**
     * MCC 树节点响应。
     */
    @Data
    public static class MccTreeNodeVO {
        /**
         * 树节点稳定键，由节点类型和业务主键组成。
         */
        private String nodeKey;
        private String nodeType;
        private Long id;
        /**
         * 父节点稳定键；一级分类节点为空。
         */
        private String parentNodeKey;
        private Integer level;
        private String code;
        private String nameCn;
        private String nameEn;
        private String label;
        private String mccCode;
        private String mccNameCn;
        private String mccNameEn;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        private String mccType;
        private String deliveryApplicability;
        private String source;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         */
        private String versionNo;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        private Integer status;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
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
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        private String deliveryApplicability;
        private String source;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         */
        private String versionNo;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
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
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String countryScope;
        private String countryCode;
        private String countryNameCn;
        private String countryNameEn;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
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
     * MCC 管理页面所需的全部稳定下拉选项集合。
     */
    @Data
    public static class MccOptionsVO {

        /**
         * 一级 MCC 分类选项。
         */
        private List<MccOptionVO> level1 = List.of();

        /**
         * 二级 MCC 分类选项。
         */
        private List<MccOptionVO> level2 = List.of();

        /**
         * MCC 四位编码选项。
         */
        private List<MccOptionVO> mccCodes = List.of();

        /**
         * 卡组织数据字典选项。
         */
        private List<MccOptionVO> cardSchemes = List.of();

        /**
         * 已启用国家或地区选项。
         */
        private List<MccOptionVO> countries = List.of();
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

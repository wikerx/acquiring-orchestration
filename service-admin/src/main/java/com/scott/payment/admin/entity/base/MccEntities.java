package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCC Entities 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class MccEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
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
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        /**
         * 持久化的{@code deliveryApplicability}，用于还原当前记录的业务事实。
         */
        private String deliveryApplicability;
        /**
         * 持久化的来源，用于还原当前记录的业务事实。
         */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
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
        /**
         * 持久化的{@code cardScheme}，用于还原当前记录的业务事实。
         */
        private String cardScheme;
        /**
         * 持久化的渠道范围，用于还原当前记录的业务事实。
         */
        private String channelScope;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String countryScope;
        private String countryCode;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        /**
         * 持久化的{@code allowOnboarding}，用于还原当前记录的业务事实。
         */
        private Integer allowOnboarding;
        /**
         * 持久化的{@code allowAcquiring}，用于还原当前记录的业务事实。
         */
        private Integer allowAcquiring;
        /**
         * 持久化的{@code requireEnhancedReview}，用于还原当前记录的业务事实。
         */
        private Integer requireEnhancedReview;
        private Integer status;
        private Integer policyStatus;
        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }
}

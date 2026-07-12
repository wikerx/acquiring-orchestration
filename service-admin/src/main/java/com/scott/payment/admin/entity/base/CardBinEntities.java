package com.scott.payment.admin.entity.base;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinEntities
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 基础数据实体集合，位于 service-admin 数据实体层，承载卡 BIN 区间和旧库初始化批次的持久化边界。
 * @status : create
 */
public final class CardBinEntities {

    private CardBinEntities() {
    }

    /**
     * 卡 BIN 区间数据库实体。
     */
    @Data
    @TableName("base_card_bin_range")
    public static class BaseCardBinRangeDO {
        /**
         * 主键 ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;

        /**
         * 旧表 card_bin_type_info.pk_id，用于初始化数据追溯。
         */
        private Long legacyPkId;

        /**
         * 卡 BIN 起始值，统一按 11 位数字区间保存。
         */
        private Long cardBinStart;

        /**
         * 卡 BIN 结束值，统一按 11 位数字区间保存。
         */
        private Long cardBinEnd;

        /**
         * BIN 精度长度，允许 6 到 11 位。
         */
        private Integer binLength;

        /**
         * 卡品牌字典值，复用系统 card_brand 字典。
         */
        private String cardBrand;

        /**
         * 卡子品牌或卡产品名称。
         */
        private String cardSubBrand;

        /**
         * 卡类型字典值，例如 CREDIT、DEBIT、PREPAID。
         */
        private String cardType;

        /**
         * 卡等级，例如 Classic、Gold、Platinum。
         */
        private String cardLevel;

        /**
         * 发卡国家或地区名称。
         */
        private String issuerCountryName;

        /**
         * 发卡国家 ISO Alpha-2 编码。
         */
        private String issuerCountryAlpha2;

        /**
         * 发卡国家 ISO Alpha-3 编码。
         */
        private String issuerCountryAlpha3;

        /**
         * 发卡国家 ISO Numeric 编码。
         */
        private String issuerCountryNumeric;

        /**
         * 发卡行名称。
         */
        private String issuerBank;

        /**
         * 发卡行官网地址。
         */
        private String issuerWebUrl;

        /**
         * 发卡行联系电话。
         */
        private String issuerTelephone;

        /**
         * 数据来源字典值，例如 MANUAL、LEGACY_DB。
         */
        private String dataSource;

        /**
         * 来源批次号，用于追溯导入批次。
         */
        private String sourceBatchNo;

        /**
         * 来源优先级，匹配时数值越大越优先。
         */
        private Integer sourcePriority;

        /**
         * 生效时间。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间。
         */
        private LocalDateTime expireTime;

        /**
         * 状态：0 禁用，1 启用，2 待确认，3 已过期。
         */
        private Integer status;

        /**
         * 备注。
         */
        private String remark;

        /**
         * 创建人。
         */
        private String createBy;

        /**
         * 修改人。
         */
        private String updateBy;

        /**
         * 创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 修改时间。
         */
        private LocalDateTime updateTime;

        /**
         * 逻辑删除标识：0 未删除，大于 0 为删除记录 ID。
         */
        private Long deleted;
    }

    /**
     * 卡 BIN 导入批次数据库实体。
     */
    @Data
    @TableName("base_card_bin_import_batch")
    public static class BaseCardBinImportBatchDO {
        /**
         * 主键 ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;

        /**
         * 批次号。
         */
        private String batchNo;

        /**
         * 导入类型：DB_INIT、EXCEL、CSV、API。
         */
        private String importType;

        /**
         * 数据来源。
         */
        private String dataSource;

        /**
         * 文件名称，数据库初始化导入可为空。
         */
        private String fileName;

        /**
         * 总条数。
         */
        private Integer totalCount;

        /**
         * 成功条数。
         */
        private Integer successCount;

        /**
         * 失败条数。
         */
        private Integer failedCount;

        /**
         * 冲突条数。
         */
        private Integer conflictCount;

        /**
         * 重复条数。
         */
        private Integer duplicateCount;

        /**
         * 状态：0 处理中，1 成功，2 部分成功，3 失败。
         */
        private Integer status;

        /**
         * 错误信息。
         */
        private String errorMessage;

        /**
         * 备注。
         */
        private String remark;

        /**
         * 创建人。
         */
        private String createBy;

        /**
         * 创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 修改时间。
         */
        private LocalDateTime updateTime;
    }
}

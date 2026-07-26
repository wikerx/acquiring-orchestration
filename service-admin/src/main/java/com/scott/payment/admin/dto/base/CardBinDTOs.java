package com.scott.payment.admin.dto.base;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinDTOs
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 管理请求和响应对象集合，位于 service-admin 接口传输层，约束 BIN 输入、匹配测试和发卡行识别结果。
 * @status : create
 */
public final class CardBinDTOs {

    private CardBinDTOs() {
    }

    /**
     * 卡 BIN 分页查询请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CardBinQueryRequest extends PageRequest {
        /**
         * 卡 BIN 查询值，仅允许 6 到 11 位数字。
         */
        private String cardBin;

        /**
         * 卡品牌字典值。
         */
        private String cardBrand;

        /**
         * 卡类型字典值。
         */
        private String cardType;

        /**
         * 发卡国家 ISO Alpha-2 编码。
         */
        private String issuerCountryAlpha2;

        /**
         * 发卡行名称，支持模糊查询。
         */
        private String issuerBank;

        /**
         * 状态：0 禁用，1 启用，2 待确认，3 已过期。
         */
        private Integer status;

        /**
         * 数据来源字典值。
         */
        private String dataSource;
    }

    /**
     * 卡 BIN 保存请求。
     */
    @Data
    public static class CardBinSaveRequest {
        /**
         * 主键 ID，新增时为空，编辑时必填。
         */
        private Long id;

        /**
         * BIN 起始值，仅允许 6 到 11 位数字。
         */
        @NotBlank(message = "cardBinStart is required")
        @Pattern(regexp = "^[0-9]{6,11}$", message = "cardBinStart must be 6 to 11 digits")
        private String cardBinStart;

        /**
         * BIN 结束值，允许为空，非空时必须与起始值长度一致。
         */
        @Pattern(regexp = "^$|^[0-9]{6,11}$", message = "cardBinEnd must be 6 to 11 digits")
        private String cardBinEnd;

        /**
         * 卡品牌字典值，复用 card_brand 字典。
         */
        @NotBlank(message = "cardBrand is required")
        private String cardBrand;

        /**
         * 卡子品牌或卡产品名称。
         */
        private String cardSubBrand;

        /**
         * 卡类型字典值。
         */
        @NotBlank(message = "cardType is required")
        private String cardType;

        /**
         * 卡等级。
         */
        private String cardLevel;

        /**
         * 发卡国家名称。
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
         * 数据来源字典值，新增默认 MANUAL。
         */
        private String dataSource;

        /**
         * 来源优先级，数值越大匹配越优先。
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
    }

    /**
     * 卡 BIN 状态更新请求。
     */
    @Data
    public static class CardBinStatusRequest {
        /**
         * 状态：0 禁用，1 启用，2 待确认，3 已过期。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 卡 BIN 匹配测试请求。
     */
    @Data
    public static class CardBinMatchRequest {
        /**
         * 卡 BIN 测试值，仅允许 6 到 11 位数字，禁止完整卡号。
         */
        @NotBlank(message = "cardBin is required")
        @Pattern(regexp = "^[0-9]{6,11}$", message = "CardBin must be 6 to 11 digits")
        private String cardBin;
    }

    /**
     * 卡 BIN 页面下拉选项。
     */
    @Data
    public static class CardBinOption {
        /**
         * 下拉展示文案。
         */
        private String label;

        /**
         * 下拉选项值。
         */
        private String value;

        /**
         * 扩展编码或 Alpha-2 编码。
         */
        private String alpha2;

        /**
         * 字典扩展 JSON，用于承载卡品牌 logoKey 等展示元数据。
         */
        private String extraJson;

        /**
         * 国家或地区旗帜 Emoji，来源于 ISO 国家/地区表。
         */
        private String flagEmoji;

        /**
         * ISO Alpha-3 编码。
         */
        private String alpha3;

        /**
         * ISO Numeric 编码。
         */
        private String numeric;

        /**
         * 国家或地区名称。
         */
        private String countryName;
    }

    /**
     * 卡 BIN 页面下拉聚合响应。
     */
    @Data
    public static class CardBinOptionsResponse {
        /**
         * 卡品牌下拉。
         */
        private List<CardBinOption> cardBrandOptions;

        /**
         * 卡类型下拉。
         */
        private List<CardBinOption> cardTypeOptions;

        /**
         * 状态下拉。
         */
        private List<CardBinOption> statusOptions;

        /**
         * 数据来源下拉。
         */
        private List<CardBinOption> dataSourceOptions;

        /**
         * 发卡国家下拉。
         */
        private List<CardBinOption> countryOptions;
    }

    /**
     * 卡 BIN 列表和详情响应。
     */
    @Data
    public static class CardBinResponse {
        /**
         * 主键 ID。
         */
        private Long id;
        /**
         * 旧表主键 ID，用于追溯初始化来源。
         */
        private Long legacyPkId;
        /**
         * BIN 起始值，11 位字符串展示。
         */
        private String cardBinStart;
        /**
         * BIN 结束值，11 位字符串展示。
         */
        private String cardBinEnd;
        /**
         * BIN 精度。
         */
        private Integer binLength;
        /**
         * 卡品牌字典值。
         */
        private String cardBrand;
        /**
         * 卡品牌展示名称。
         */
        private String cardBrandName;
        /**
         * 卡子品牌。
         */
        private String cardSubBrand;
        /**
         * 卡类型字典值。
         */
        private String cardType;
        /**
         * 卡类型展示名称。
         */
        private String cardTypeName;
        /**
         * 卡等级。
         */
        private String cardLevel;
        /**
         * 发卡国家名称。
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
         * 数据来源字典值。
         */
        private String dataSource;
        /**
         * 数据来源展示名称。
         */
        private String dataSourceName;
        /**
         * 来源批次号。
         */
        private String sourceBatchNo;
        /**
         * 来源优先级。
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
         * 状态。
         */
        private Integer status;
        /**
         * 状态展示名称。
         */
        private String statusName;
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
    }

    /**
     * 卡 BIN 匹配测试响应。
     */
    @Data
    public static class CardBinMatchResponse {
        /**
         * 是否命中。
         */
        private Boolean matched;
        /**
         * 命中数量。
         */
        private Integer matchCount;
        /**
         * 最优匹配。
         */
        private CardBinResponse bestMatch;
        /**
         * 命中列表。
         */
        private List<CardBinResponse> matches;
    }

    /**
     * 卡 BIN 导入批次响应。
     */
    @Data
    public static class CardBinImportBatchResponse {
        /**
         * 主键 ID。
         */
        private Long id;
        /**
         * 批次号。
         */
        private String batchNo;
        /**
         * 导入类型。
         */
        private String importType;
        /**
         * 数据来源。
         */
        private String dataSource;
        /**
         * 文件名称。
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
         * 状态。
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

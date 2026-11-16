package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data 分页读取管理端平台级导出源的内部契约，禁止浏览器直接调用。
 */
public final class AdminPlatformExportInternalDTOs {

    /**
     * 阻止内部契约容器被实例化。
     */
    private AdminPlatformExportInternalDTOs() {
    }

    /**
     * 卡 BIN 冻结查询条件，不包含页面分页参数。
     */
    @Data
    public static class CardBinQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cardBin;
        private String cardBrand;
        private String cardType;
        private String issuerCountryAlpha2;
        private String issuerBank;
        private Integer status;
        private String dataSource;
    }

    /** 国家和地区冻结查询条件，不包含页面分页参数。 */
    @Data
    public static class CountryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
        private Integer status;
    }

    /** 币种冻结查询条件，不包含页面分页参数。 */
    @Data
    public static class CurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private Integer status;
    }

    /** 国家默认币种关系冻结查询条件，不包含页面分页参数。 */
    @Data
    public static class RegionCurrencyQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String continentCode;
    }

    /** MCC 编码冻结查询条件，保留树列表的全部公开筛选字段。 */
    @Data
    public static class MccCodeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String mccCode;
        private String nameCn;
        private String nameEn;
        private String nodeType;
        private String riskLevel;
        private Integer status;
    }

    /**
     * 任务运行日志冻结查询条件，不包含分页和日志正文内容。
     */
    @Data
    public static class JobRunLogQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long jobId;
        private String jobCode;
        private String runStatus;
        private String triggerType;
        private String executorNode;
    }

    /**
     * Data 分页读取卡 BIN 安全投影的内部请求。
     */
    @Data
    public static class CardBinPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private CardBinQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * Data 分页读取任务运行日志安全投影的内部请求。
     */
    @Data
    public static class JobRunLogPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private JobRunLogQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取国家和地区安全投影的内部请求。 */
    @Data
    public static class CountryPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private CountryQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取币种安全投影的内部请求。 */
    @Data
    public static class CurrencyPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private CurrencyQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取国家默认币种关系安全投影的内部请求。 */
    @Data
    public static class RegionCurrencyPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private RegionCurrencyQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取 MCC 编码安全投影的内部请求。 */
    @Data
    public static class MccCodePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private MccCodeQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 一页卡 BIN 安全投影及相同条件下的总行数。
     */
    @Data
    public static class CardBinPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<CardBinRow> records = new ArrayList<>();
    }

    /**
     * 一页任务运行日志安全投影及相同条件下的总行数。
     */
    @Data
    public static class JobRunLogPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<JobRunLogRow> records = new ArrayList<>();
    }

    /** 一页国家和地区安全投影及相同条件下的总行数。 */
    @Data
    public static class CountryPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<CountryRow> records = new ArrayList<>();
    }

    /** 一页币种安全投影及相同条件下的总行数。 */
    @Data
    public static class CurrencyPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<CurrencyRow> records = new ArrayList<>();
    }

    /** 一页国家默认币种关系安全投影及相同条件下的总行数。 */
    @Data
    public static class RegionCurrencyPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RegionCurrencyRow> records = new ArrayList<>();
    }

    /** 一页 MCC 编码安全投影及相同条件下的总行数。 */
    @Data
    public static class MccCodePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<MccCodeRow> records = new ArrayList<>();
    }

    /**
     * 卡 BIN 导出安全行，不包含数据库主键和历史迁移标识。
     */
    @Data
    public static class CardBinRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cardBinStart;
        private String cardBinEnd;
        private Integer binLength;
        private String cardBrand;
        private String cardSubBrand;
        private String cardType;
        private String cardLevel;
        private String issuerCountryName;
        private String issuerCountryAlpha2;
        private String issuerCountryAlpha3;
        private String issuerCountryNumeric;
        private String issuerBank;
        private String issuerWebUrl;
        private String issuerTelephone;
        private String dataSource;
        private String status;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
        private String createBy;
        private String updateBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 任务运行日志导出安全行，不包含参数快照、完整结果、追踪标识或操作人信息。
     */
    @Data
    public static class JobRunLogRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String runId;
        private String jobCode;
        private String jobName;
        private String triggerType;
        private String runStatus;
        private String executorNode;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private String errorMessage;
        private LocalDateTime createTime;
    }

    /** 国家和地区导出安全行，不包含数据库主键和删除标记。 */
    @Data
    public static class CountryRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String alpha2Code;
        private String alpha3Code;
        private String chineseName;
        private String englishName;
        private String continentName;
        private String currencyAlpha3Code;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /** 币种导出安全行，数字代码保持文本语义。 */
    @Data
    public static class CurrencyRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String alpha3Code;
        private String numericCode;
        private String chineseName;
        private String englishName;
        private String currencySymbol;
        private Integer fractionDigits;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /** 国家默认币种关系导出安全行。 */
    @Data
    public static class RegionCurrencyRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String alpha2Code;
        private String countryName;
        private String continentName;
        private String currencyAlpha3Code;
        private String currencyName;
        private String currencySymbol;
        private Integer status;
    }

    /** MCC 编码导出安全行，不包含数据库主键和内部关联主键。 */
    @Data
    public static class MccCodeRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mccCode;
        private String nameCn;
        private String nameEn;
        private String level1Name;
        private String level2Name;
        private String mccType;
        private String riskLevel;
        private String deliveryApplicability;
        private Integer status;
        private String source;
        private String versionNo;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
    }

    /**
     * 部门导出查询标记；独立空类型用于约束任务类型和快照一一对应。
     */
    @Data
    public static class DepartmentQuery implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * 岗位导出冻结查询条件，不包含浏览器分页参数。
     */
    @Data
    public static class PostQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String postCode;
        private String postName;
        private Integer status;
    }

    /**
     * 字典类型导出冻结查询条件，不包含浏览器分页参数。
     */
    @Data
    public static class DictTypeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictName;
        private String dictType;
        private String bizDomain;
        private Integer status;
    }

    /**
     * 字典数据导出冻结查询条件，不包含浏览器分页参数。
     */
    @Data
    public static class DictDataQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictType;
        private String dictLabel;
        private String dictValue;
        private String parentValue;
        private String locale;
        private Integer status;
    }

    /**
     * 节假日日历导出冻结查询条件，仅保存所选自然年。
     */
    @Data
    public static class HolidayCalendarQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Min(2000)
        @Max(2100)
        private int year;
    }

    /**
     * Data 分页读取部门导出源的内部请求，包含可信创建人和受控分页参数。
     */
    @Data
    public static class DepartmentPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private DepartmentQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * Data 分页读取岗位导出源的内部请求，包含冻结筛选和受控分页参数。
     */
    @Data
    public static class PostPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private PostQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * Data 分页读取字典类型导出源的内部请求，包含冻结筛选和受控分页参数。
     */
    @Data
    public static class DictTypePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private DictTypeQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * Data 分页读取字典数据导出源的内部请求，包含冻结字典范围和受控分页参数。
     */
    @Data
    public static class DictDataPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private DictDataQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * Data 分页读取节假日日历导出源的内部请求，包含冻结自然年和受控分页参数。
     */
    @Data
    public static class HolidayCalendarPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private HolidayCalendarQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 一页部门安全投影及相同条件下的总行数。
     */
    @Data
    public static class DepartmentPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<DepartmentRow> records = new ArrayList<>();
    }

    /**
     * 一页岗位安全投影及相同条件下的总行数。
     */
    @Data
    public static class PostPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<PostRow> records = new ArrayList<>();
    }

    /**
     * 一页字典类型安全投影及相同条件下的总行数。
     */
    @Data
    public static class DictTypePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<DictTypeRow> records = new ArrayList<>();
    }

    /**
     * 一页字典数据安全投影及相同条件下的总行数。
     */
    @Data
    public static class DictDataPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<DictDataRow> records = new ArrayList<>();
    }

    /**
     * 一页节假日日历安全投影及相同条件下的总行数。
     */
    @Data
    public static class HolidayCalendarPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<HolidayCalendarRow> records = new ArrayList<>();
    }

    /**
     * 部门导出安全行，字段与既有同步导出保持兼容。
     */
    @Data
    public static class DepartmentRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deptName;
        private String parentName;
        private Integer sortNo;
        private String leader;
        private String phone;
        private String email;
        private Integer status;
    }

    /**
     * 岗位导出安全行，不包含数据库内部主键和删除标记。
     */
    @Data
    public static class PostRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String postCode;
        private String postName;
        private Integer sortNo;
        private Integer status;
        private String remark;
        private LocalDateTime createdAt;
    }

    /**
     * 字典类型导出安全行，不包含数据库内部主键和编辑控制字段。
     */
    @Data
    public static class DictTypeRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictName;
        private String dictType;
        private String bizDomain;
        private Integer status;
        private Integer systemBuiltin;
        private LocalDateTime createdAt;
    }

    /**
     * 字典数据导出安全行，编码和值保持文本语义。
     */
    @Data
    public static class DictDataRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dictType;
        private String dictLabel;
        private String dictValue;
        private String locale;
        private Integer dictSort;
        private Integer status;
        private Integer defaultFlag;
        private LocalDateTime createdAt;
    }

    /**
     * 节假日日历导出安全行，仅保留页面公开字段和必要更新时间。
     */
    @Data
    public static class HolidayCalendarRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDate calendarDate;
        private Integer dayOfWeek;
        private String dayType;
        private String holidayName;
        private Boolean statutoryHoliday;
        private Boolean adjustedWorkday;
        private String remark;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 安全拦截事件冻结查询条件，不包含页面分页参数。 */
    @Data
    public static class SecurityInterceptEventQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private String sourceLayer;
        private String eventType;
        private String riskLevel;
        private String action;
        private String merchantId;
        private String clientIp;
        private String requestPath;
        private String traceId;
        private String requestId;
        private String hitRuleCode;
        private Integer processStatus;
        private String queryTimeZone;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
    }

    /** Data 分页读取安全拦截事件的内部请求。 */
    @Data
    public static class SecurityInterceptEventPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private SecurityInterceptEventQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** 一页安全拦截事件安全投影。 */
    @Data
    public static class SecurityInterceptEventPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SecurityInterceptEventRow> records = new ArrayList<>();
    }

    /** 安全拦截事件安全导出行，不包含请求头、User-Agent 或原始载荷。 */
    @Data
    public static class SecurityInterceptEventRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private LocalDateTime eventTime;
        private String sourceLayer;
        private String eventType;
        private String riskLevel;
        private String action;
        private String merchantId;
        private String clientIp;
        private String requestPath;
        private String traceId;
        private String hitRuleCode;
        private String reasonCode;
        private String reasonMessage;
        private Integer processStatus;
        private String processedBy;
        private LocalDateTime processedTime;
    }

    /** 系统账户冻结查询条件，不包含浏览器分页参数。 */
    @Data
    public static class SystemUserQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String loginAccount;
        private String mobile;
        private String email;
        private Long deptId;
        private Integer status;
        private String emailVerificationStatus;
        private String activationStatus;
    }

    /** 系统参数冻结查询条件，不包含浏览器分页参数。 */
    @Data
    public static class SystemConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String configName;
        private String configKey;
        private String configGroup;
        private Integer status;
    }

    /** 商户 IP 白名单冻结查询条件，不包含浏览器分页参数。 */
    @Data
    public static class MerchantIpWhitelistQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantKeyword;
        private String ipValue;
        private String ipType;
        private Integer status;
        private Integer approvalStatus;
        private String submitSource;
    }

    /** 一页系统账户导出来源请求。 */
    @Data
    public static class SystemUserPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        @Valid
        @NotNull
        private SystemUserQuery query;
        @Min(1)
        private Integer pageNo;
        @Min(1)
        @Max(1000)
        private Integer pageSize;
    }

    /** 一页系统参数导出来源请求。 */
    @Data
    public static class SystemConfigPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        @Valid
        @NotNull
        private SystemConfigQuery query;
        @Min(1)
        private Integer pageNo;
        @Min(1)
        @Max(1000)
        private Integer pageSize;
    }

    /** 一页商户 IP 白名单导出来源请求。 */
    @Data
    public static class MerchantIpWhitelistPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private MerchantIpWhitelistQuery query;
        @Min(1)
        private Integer pageNo;
        @Min(1)
        @Max(1000)
        private Integer pageSize;
    }

    /** 一页系统账户安全投影。 */
    @Data
    public static class SystemUserPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SystemUserRow> records = new ArrayList<>();
    }

    /** 一页系统参数安全投影。 */
    @Data
    public static class SystemConfigPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SystemConfigRow> records = new ArrayList<>();
    }

    /** 一页商户 IP 白名单安全投影。 */
    @Data
    public static class MerchantIpWhitelistPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<MerchantIpWhitelistRow> records = new ArrayList<>();
    }

    /** 系统账户导出安全行，与既有同步导出列保持一致。 */
    @Data
    public static class SystemUserRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String loginAccount;
        private String realName;
        private String deptName;
        private List<String> postNames = new ArrayList<>();
        private List<String> roleNames = new ArrayList<>();
        private String mobile;
        private String email;
        private Integer status;
        private Integer locked;
        private LocalDateTime lastLoginAt;
        private String lastLoginIp;
        private String remark;
        private LocalDateTime createdAt;
    }

    /** 系统参数导出安全行，configValue 已在 service-admin 完成脱敏。 */
    @Data
    public static class SystemConfigRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String configName;
        private String configKey;
        private String configGroup;
        private String configValue;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /** 商户 IP 白名单导出安全行，按商户聚合当前查询命中的 IP。 */
    @Data
    public static class MerchantIpWhitelistRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantName;
        private String merchantShortName;
        private List<MerchantIpWhitelistItemRow> ipWhitelists = new ArrayList<>();
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 商户 IP 白名单聚合行中的单个 IP 安全投影。 */
    @Data
    public static class MerchantIpWhitelistItemRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ipType;
        private String ipValue;
        private Integer status;
    }
}

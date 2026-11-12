package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CardBinQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CountryQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DepartmentQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DictDataQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DictTypeQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.HolidayCalendarQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.JobRunLogQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MccCodeQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PostQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RegionCurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SecurityInterceptEventQuery;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data 调用 Admin 平台级导出来源的强类型内部模型。
 */
public final class AdminPlatformExportSourceDTOs {

    /**
     * 阻止内部模型容器被实例化。
     */
    private AdminPlatformExportSourceDTOs() {
    }

    /**
     * 一页卡 BIN 导出来源请求。
     */
    @Data
    public static class CardBinPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private CardBinQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页任务运行日志导出来源请求。
     */
    @Data
    public static class JobRunLogPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private JobRunLogQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页国家和地区导出来源请求。
     */
    @Data
    public static class CountryPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private CountryQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页币种导出来源请求。
     */
    @Data
    public static class CurrencyPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private CurrencyQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页国家默认币种关系导出来源请求。
     */
    @Data
    public static class RegionCurrencyPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private RegionCurrencyQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页 MCC 编码导出来源请求。
     */
    @Data
    public static class MccCodePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private MccCodeQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页卡 BIN 安全投影及总行数。
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
     * 一页任务运行日志安全投影及总行数。
     */
    @Data
    public static class JobRunLogPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<JobRunLogRow> records = new ArrayList<>();
    }

    /**
     * 一页国家和地区安全投影。
     */
    @Data
    public static class CountryPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<CountryRow> records = new ArrayList<>();
    }

    /**
     * 一页币种安全投影。
     */
    @Data
    public static class CurrencyPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<CurrencyRow> records = new ArrayList<>();
    }

    /**
     * 一页国家默认币种关系安全投影。
     */
    @Data
    public static class RegionCurrencyPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RegionCurrencyRow> records = new ArrayList<>();
    }

    /**
     * 一页 MCC 编码安全投影。
     */
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
     * 任务运行日志导出安全行，不包含参数快照、结果原文或跟踪标识。
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

    /**
     * 国家和地区导出安全行。
     */
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

    /**
     * 币种导出安全行，数字代码保持字符串类型，避免 Excel 丢失前导零。
     */
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

    /**
     * 国家默认币种关系导出安全行。
     */
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

    /**
     * MCC 编码导出安全行，编码保持字符串类型，避免 Excel 丢失前导零。
     */
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
     * 一页部门导出来源请求，携带创建人身份和冻结查询快照。
     */
    @Data
    public static class DepartmentPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private DepartmentQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页岗位导出来源请求，分页参数由 Data 导出执行器控制。
     */
    @Data
    public static class PostPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private PostQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页字典类型导出来源请求，分页参数由 Data 导出执行器控制。
     */
    @Data
    public static class DictTypePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private DictTypeQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页字典数据导出来源请求，包含冻结字典范围和公开筛选条件。
     */
    @Data
    public static class DictDataPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private DictDataQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页节假日日历导出来源请求，只允许读取冻结自然年。
     */
    @Data
    public static class HolidayCalendarPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private HolidayCalendarQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页部门安全投影响应，不包含内部鉴权或数据范围信息。
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
     * 一页岗位安全投影响应，不包含内部鉴权或数据范围信息。
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
     * 一页字典类型安全投影响应，不包含内部鉴权或数据范围信息。
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
     * 一页字典数据安全投影响应，不包含内部鉴权或数据范围信息。
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
     * 一页节假日日历安全投影响应，不包含内部鉴权或数据范围信息。
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
     * 岗位导出安全行，只保留管理端既有导出字段。
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
     * 字典类型导出安全行，只保留管理端既有导出字段。
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
     * 节假日日历导出安全行，只保留年度维护页面公开字段。
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

    /**
     * 一页安全拦截事件导出来源请求。
     */
    @Data
    public static class SecurityInterceptEventPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private SecurityInterceptEventQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页安全拦截事件安全投影。
     */
    @Data
    public static class SecurityInterceptEventPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SecurityInterceptEventRow> records = new ArrayList<>();
    }

    /**
     * 安全拦截事件安全导出行。
     */
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

    /** 系统账户冻结查询条件。 */
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

    /** 系统参数冻结查询条件。 */
    @Data
    public static class SystemConfigQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String configName;
        private String configKey;
        private String configGroup;
        private Integer status;
    }

    /** 商户 IP 白名单冻结查询条件。 */
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
        private SystemUserQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 一页系统参数导出来源请求。 */
    @Data
    public static class SystemConfigPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private SystemConfigQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 一页商户 IP 白名单导出来源请求。 */
    @Data
    public static class MerchantIpWhitelistPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private MerchantIpWhitelistQuery query;
        private Integer pageNo;
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

    /** 系统账户导出安全行。 */
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

    /** 系统参数导出安全行，参数值已由 Admin 脱敏。 */
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

    /** 商户 IP 白名单按商户聚合导出行。 */
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

    /** 聚合白名单行中的单个 IP。 */
    @Data
    public static class MerchantIpWhitelistItemRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ipType;
        private String ipValue;
        private Integer status;
    }
}

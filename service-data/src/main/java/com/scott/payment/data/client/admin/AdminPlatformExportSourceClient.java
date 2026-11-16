package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageResponse;

/**
 * service-data 分页读取管理端平台级导出源的内部客户端契约。
 */
public interface AdminPlatformExportSourceClient {

    /** 按冻结功能和条件读取一页风控配置安全投影。 */
    RiskPageResponse loadRiskConfigPage(RiskPageRequest request);

    /** 读取一页数据源脱敏运行快照。 */
    DatasourcePageResponse loadDatasourceSnapshotPage(DatasourcePageRequest request);

    /**
     * 按冻结条件读取一页卡 BIN 安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return 卡 BIN 分页
     */
    CardBinPageResponse loadCardBinPage(CardBinPageRequest request);

    /**
     * 按冻结条件读取一页国家和地区安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return 国家和地区分页
     */
    CountryPageResponse loadCountryPage(CountryPageRequest request);

    /**
     * 按冻结条件读取一页币种安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return 币种分页
     */
    CurrencyPageResponse loadCurrencyPage(CurrencyPageRequest request);

    /**
     * 按冻结条件读取一页国家默认币种关系安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return 国家默认币种关系分页
     */
    RegionCurrencyPageResponse loadRegionCurrencyPage(RegionCurrencyPageRequest request);

    /**
     * 按冻结条件读取一页 MCC 编码安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return MCC 编码分页
     */
    MccCodePageResponse loadMccCodePage(MccCodePageRequest request);

    /**
     * 按冻结条件读取一页部门安全投影，禁止返回未授权的管理端字段。
     */
    DepartmentPageResponse loadDepartmentPage(DepartmentPageRequest request);

    /**
     * 按冻结条件读取一页岗位安全投影，分页参数由导出执行器控制。
     */
    PostPageResponse loadPostPage(PostPageRequest request);

    /**
     * 按冻结条件读取一页字典类型安全投影，分页参数由导出执行器控制。
     */
    DictTypePageResponse loadDictTypePage(DictTypePageRequest request);

    /**
     * 按冻结字典范围读取一页字典数据安全投影。
     */
    DictDataPageResponse loadDictDataPage(DictDataPageRequest request);

    /**
     * 按冻结自然年读取一页节假日日历安全投影。
     */
    HolidayCalendarPageResponse loadHolidayCalendarPage(HolidayCalendarPageRequest request);

    /**
     * 按冻结条件读取一页任务运行日志安全投影。
     *
     * @param request 冻结条件和内部分页参数
     * @return 任务运行日志分页
     */
    JobRunLogPageResponse loadJobRunLogPage(JobRunLogPageRequest request);

    /**
     * 按冻结条件和商户范围读取一页安全拦截事件。
     *
     * @param request 冻结条件、商户范围和内部分页参数
     * @return 安全拦截事件分页
     */
    SecurityInterceptEventPageResponse loadSecurityInterceptEventPage(
            SecurityInterceptEventPageRequest request);

    /** 按冻结条件读取一页系统账户安全投影。 */
    SystemUserPageResponse loadSystemUserPage(SystemUserPageRequest request);

    /** 按冻结条件读取一页已脱敏系统参数安全投影。 */
    SystemConfigPageResponse loadSystemConfigPage(SystemConfigPageRequest request);

    /** 按冻结条件和商户范围读取一页商户 IP 白名单安全投影。 */
    MerchantIpWhitelistPageResponse loadMerchantIpWhitelistPage(
            MerchantIpWhitelistPageRequest request);
}

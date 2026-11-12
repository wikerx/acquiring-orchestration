package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminPlatformExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemUserPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemUserPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistPageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的平台级导出源内部入口，受 HMAC 和精确路径白名单保护。
 */
@RestController
@RequestMapping("/internal/admin")
public class AdminPlatformExportInternalController {
    private final AdminPlatformExportSourceApplicationService applicationService;

    /**
     * 创建仅供异步导出执行器调用的平台级来源入口。
     *
     * @param applicationService 平台级导出来源服务
     */
    public AdminPlatformExportInternalController(
            AdminPlatformExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按冻结条件分页读取卡 BIN 安全投影。
     */
    @PostMapping("/base/card-bin/export-page")
    public CommonResult<CardBinPageResponse> cardBinPage(
            @Valid @RequestBody CardBinPageRequest request) {
        return success(applicationService.pageCardBins(request));
    }

    /** 按冻结条件分页读取国家和地区安全投影。 */
    @PostMapping("/base/countries/export-page")
    public CommonResult<CountryPageResponse> countryPage(
            @Valid @RequestBody CountryPageRequest request) {
        return success(applicationService.pageCountries(request));
    }

    /** 按冻结条件分页读取币种安全投影。 */
    @PostMapping("/base/currencies/export-page")
    public CommonResult<CurrencyPageResponse> currencyPage(
            @Valid @RequestBody CurrencyPageRequest request) {
        return success(applicationService.pageCurrencies(request));
    }

    /** 按冻结条件分页读取国家默认币种关系安全投影。 */
    @PostMapping("/base/region-currencies/export-page")
    public CommonResult<RegionCurrencyPageResponse> regionCurrencyPage(
            @Valid @RequestBody RegionCurrencyPageRequest request) {
        return success(applicationService.pageRegionCurrencies(request));
    }

    /** 按冻结条件分页读取 MCC 编码安全投影。 */
    @PostMapping("/base/mcc-codes/export-page")
    public CommonResult<MccCodePageResponse> mccCodePage(
            @Valid @RequestBody MccCodePageRequest request) {
        return success(applicationService.pageMccCodes(request));
    }

    /**
     * 按冻结条件分页读取部门安全投影，仅供 service-data 内部调用。
     */
    @PostMapping("/system/departments/export-page")
    public CommonResult<DepartmentPageResponse> departmentPage(
            @Valid @RequestBody DepartmentPageRequest request) {
        return success(applicationService.pageDepartments(request));
    }

    /**
     * 按冻结条件分页读取岗位安全投影，仅供 service-data 内部调用。
     */
    @PostMapping("/system/posts/export-page")
    public CommonResult<PostPageResponse> postPage(@Valid @RequestBody PostPageRequest request) {
        return success(applicationService.pagePosts(request));
    }

    /**
     * 按冻结条件分页读取字典类型安全投影，仅供 service-data 内部调用。
     */
    @PostMapping("/system/dict-types/export-page")
    public CommonResult<DictTypePageResponse> dictTypePage(
            @Valid @RequestBody DictTypePageRequest request) {
        return success(applicationService.pageDictTypes(request));
    }

    /**
     * 按冻结条件分页读取字典数据安全投影，仅供 service-data 内部调用。
     */
    @PostMapping("/system/dict-data/export-page")
    public CommonResult<DictDataPageResponse> dictDataPage(
            @Valid @RequestBody DictDataPageRequest request) {
        return success(applicationService.pageDictData(request));
    }

    /**
     * 按冻结自然年分页读取节假日日历安全投影，仅供 service-data 内部调用。
     */
    @PostMapping("/system/holiday-calendar/export-page")
    public CommonResult<HolidayCalendarPageResponse> holidayCalendarPage(
            @Valid @RequestBody HolidayCalendarPageRequest request) {
        return success(applicationService.pageHolidayCalendar(request));
    }

    /**
     * 按冻结条件分页读取任务运行日志安全投影。
     */
    @PostMapping("/monitor/job-logs/export-page")
    public CommonResult<JobRunLogPageResponse> jobRunLogPage(
            @Valid @RequestBody JobRunLogPageRequest request) {
        return success(applicationService.pageJobRunLogs(request));
    }

    /**
     * 按冻结条件和商户范围分页读取安全拦截事件安全投影。
     */
    @PostMapping("/security/intercept-events/export-page")
    public CommonResult<SecurityInterceptEventPageResponse> securityInterceptEventPage(
            @Valid @RequestBody SecurityInterceptEventPageRequest request) {
        return success(applicationService.pageSecurityInterceptEvents(request));
    }

    /** 按冻结条件分页读取系统账户安全投影。 */
    @PostMapping("/system/users/export-page")
    public CommonResult<SystemUserPageResponse> systemUserPage(
            @Valid @RequestBody SystemUserPageRequest request) {
        return success(applicationService.pageSystemUsers(request));
    }

    /** 按冻结条件分页读取已脱敏的系统参数安全投影。 */
    @PostMapping("/system/configs/export-page")
    public CommonResult<SystemConfigPageResponse> systemConfigPage(
            @Valid @RequestBody SystemConfigPageRequest request) {
        return success(applicationService.pageSystemConfigs(request));
    }

    /** 按冻结条件和商户范围分页读取商户 IP 白名单安全投影。 */
    @PostMapping("/merchant/ip-whitelists/export-page")
    public CommonResult<MerchantIpWhitelistPageResponse> merchantIpWhitelistPage(
            @Valid @RequestBody MerchantIpWhitelistPageRequest request) {
        return success(applicationService.pageMerchantIpWhitelists(request));
    }
}

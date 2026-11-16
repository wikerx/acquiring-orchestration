package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.base.AdminBaseCardBinApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCountryApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.admin.application.base.AdminBaseMccApplicationService;
import com.scott.payment.admin.application.base.AdminBaseRegionCurrencyApplicationService;
import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.application.system.AdminUserApplicationService;
import com.scott.payment.admin.application.system.AdminConfigApplicationService;
import com.scott.payment.admin.application.system.SystemConfigExportValuePolicy;
import com.scott.payment.admin.application.merchant.AdminMerchantIpWhitelistApplicationService;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistItem;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinQueryRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinResponse;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.Response;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.admin.dto.base.MccVO;
import com.scott.payment.admin.dto.base.RegionCurrencyDTOs.RegionCurrencyResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypeRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodeRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemUserPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemUserPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemUserRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistRow;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MerchantIpWhitelistItemRow;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDayResponse;
import com.scott.payment.admin.service.AdminDeptService;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.admin.service.AdminHolidayCalendarService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminPostService;
import com.scott.payment.admin.service.AdminSecurityInterceptEventService;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端平台级异步导出来源服务，统一复用现有分页查询并输出最小安全投影。
 */
@Service
public class AdminPlatformExportSourceApplicationService {
    private final AdminBaseCardBinApplicationService cardBinApplicationService;
    private final AdminBaseCountryApplicationService countryApplicationService;
    private final AdminBaseCurrencyApplicationService currencyApplicationService;
    private final AdminBaseRegionCurrencyApplicationService regionCurrencyApplicationService;
    private final AdminBaseMccApplicationService mccApplicationService;
    private final AdminJobSchedulerApplicationService jobSchedulerApplicationService;
    private final AdminSecurityInterceptEventService securityInterceptEventService;
    private final AdminDeptService deptService;
    private final AdminPostService postService;
    private final AdminDictService dictService;
    private final AdminHolidayCalendarService holidayCalendarService;
    private final AdminUserApplicationService userApplicationService;
    private final AdminConfigApplicationService configApplicationService;
    private final AdminMerchantIpWhitelistApplicationService merchantIpWhitelistApplicationService;
    private final SystemConfigExportValuePolicy configExportValuePolicy;

    /**
     * 创建平台级异步导出来源服务。
     *
     * @param cardBinApplicationService 卡 BIN 权威分页服务
     * @param countryApplicationService 国家和地区权威分页服务
     * @param currencyApplicationService 币种权威分页服务
     * @param regionCurrencyApplicationService 国家默认币种关系权威分页服务
     * @param mccApplicationService MCC 编码权威分页服务
     * @param jobSchedulerApplicationService 任务调度权威分页服务
     * @param securityInterceptEventService 安全拦截事件权威分页服务
     * @param deptService 部门权威查询服务
     * @param postService 岗位权威查询服务
     * @param dictService 字典权威查询服务
     * @param holidayCalendarService 节假日日历权威查询服务
     * @param userApplicationService 系统账户权威分页服务
     * @param configApplicationService 系统参数权威分页服务
     * @param merchantIpWhitelistApplicationService 商户 IP 白名单权威分页服务
     * @param configExportValuePolicy 系统参数导出值安全策略
     */
    public AdminPlatformExportSourceApplicationService(
            AdminBaseCardBinApplicationService cardBinApplicationService,
            AdminBaseCountryApplicationService countryApplicationService,
            AdminBaseCurrencyApplicationService currencyApplicationService,
            AdminBaseRegionCurrencyApplicationService regionCurrencyApplicationService,
            AdminBaseMccApplicationService mccApplicationService,
            AdminJobSchedulerApplicationService jobSchedulerApplicationService,
            AdminSecurityInterceptEventService securityInterceptEventService,
            AdminDeptService deptService,
            AdminPostService postService,
            AdminDictService dictService,
            AdminHolidayCalendarService holidayCalendarService,
            AdminUserApplicationService userApplicationService,
            AdminConfigApplicationService configApplicationService,
            AdminMerchantIpWhitelistApplicationService merchantIpWhitelistApplicationService,
            SystemConfigExportValuePolicy configExportValuePolicy) {
        this.cardBinApplicationService = cardBinApplicationService;
        this.countryApplicationService = countryApplicationService;
        this.currencyApplicationService = currencyApplicationService;
        this.regionCurrencyApplicationService = regionCurrencyApplicationService;
        this.mccApplicationService = mccApplicationService;
        this.jobSchedulerApplicationService = jobSchedulerApplicationService;
        this.securityInterceptEventService = securityInterceptEventService;
        this.deptService = deptService;
        this.postService = postService;
        this.dictService = dictService;
        this.holidayCalendarService = holidayCalendarService;
        this.userApplicationService = userApplicationService;
        this.configApplicationService = configApplicationService;
        this.merchantIpWhitelistApplicationService = merchantIpWhitelistApplicationService;
        this.configExportValuePolicy = configExportValuePolicy;
    }

    /**
     * 按冻结条件分页读取卡 BIN 数据，内部请求覆盖任何原页面分页值。
     *
     * @param request Data 内部分页请求
     * @return 卡 BIN 安全投影分页
     */
    public CardBinPageResponse pageCardBins(CardBinPageRequest request) {
        PageResult<CardBinResponse> page = cardBinApplicationService.page(toCardBinQuery(request));
        CardBinPageResponse response = new CardBinPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toCardBinRow).toList());
        return response;
    }

    /**
     * 按冻结条件分页读取国家和地区并输出导出需要的最小字段集合。
     *
     * @param request Data 内部分页请求
     * @return 国家和地区安全投影分页
     */
    public CountryPageResponse pageCountries(CountryPageRequest request) {
        var query = request.getQuery();
        PageResult<Response> page = countryApplicationService.pageCountries(
                request.getPageNo(), request.getPageSize(), query.getKeyword(),
                query.getContinentCode(), query.getStatus());
        CountryPageResponse response = new CountryPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toCountryRow).toList());
        return response;
    }

    /**
     * 按冻结条件分页读取币种并输出导出需要的最小字段集合。
     *
     * @param request Data 内部分页请求
     * @return 币种安全投影分页
     */
    public CurrencyPageResponse pageCurrencies(CurrencyPageRequest request) {
        var query = request.getQuery();
        PageResult<com.scott.payment.admin.dto.base.IsoCurrencyDTOs.Response> page =
                currencyApplicationService.pageCurrencies(
                        request.getPageNo(), request.getPageSize(), query.getKeyword(), query.getStatus());
        CurrencyPageResponse response = new CurrencyPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toCurrencyRow).toList());
        return response;
    }

    /**
     * 按冻结条件分页读取国家默认币种关系并输出安全投影。
     *
     * @param request Data 内部分页请求
     * @return 国家默认币种关系安全投影分页
     */
    public RegionCurrencyPageResponse pageRegionCurrencies(RegionCurrencyPageRequest request) {
        var query = request.getQuery();
        PageResult<RegionCurrencyResponse> page = regionCurrencyApplicationService.pageRegionCurrencies(
                request.getPageNo(), request.getPageSize(), query.getKeyword(), query.getContinentCode());
        RegionCurrencyPageResponse response = new RegionCurrencyPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toRegionCurrencyRow).toList());
        return response;
    }

    /**
     * 按冻结树查询条件分页读取 MCC 编码并输出安全投影。
     *
     * @param request Data 内部分页请求
     * @return MCC 编码安全投影分页
     */
    public MccCodePageResponse pageMccCodes(MccCodePageRequest request) {
        PageResult<MccVO.MccCodeVO> page = mccApplicationService.pageCodesForExport(
                request.getPageNo(), request.getPageSize(), toMccQuery(request));
        MccCodePageResponse response = new MccCodePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toMccCodeRow).toList());
        return response;
    }

    /**
     * 分页读取部门并补齐上级部门名称；主记录保持数据库分页，名称索引只承载组织层级展示。
     */
    public DepartmentPageResponse pageDepartments(DepartmentPageRequest request) {
        PageResult<SysDeptDO> page = deptService.pageDepts(request.getPageNo(), request.getPageSize());
        Map<Long, String> departmentNames = deptService.exportDepts().stream()
                .collect(Collectors.toMap(SysDeptDO::getId, SysDeptDO::getDeptName,
                        (left, right) -> left));
        DepartmentPageResponse response = new DepartmentPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream()
                .map(row -> toDepartmentRow(row, departmentNames)).toList());
        return response;
    }

    /**
     * 按冻结筛选分页读取岗位最小安全投影，不返回数据库内部标识。
     */
    public PostPageResponse pagePosts(PostPageRequest request) {
        var query = request.getQuery();
        PageResult<SysPostDO> page = postService.pagePosts(
                request.getPageNo(), request.getPageSize(), query.getPostCode(),
                query.getPostName(), query.getStatus());
        PostPageResponse response = new PostPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toPostRow).toList());
        return response;
    }

    /**
     * 按冻结筛选分页读取字典类型最小安全投影，不返回编辑控制等内部字段。
     */
    public DictTypePageResponse pageDictTypes(DictTypePageRequest request) {
        PageResult<SysDictTypeDTO> page = dictService.pageDictTypes(toDictTypeQuery(request));
        DictTypePageResponse response = new DictTypePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toDictTypeRow).toList());
        return response;
    }

    /**
     * 按冻结筛选分页读取字典数据最小安全投影，不返回扩展 JSON 等内部字段。
     */
    public DictDataPageResponse pageDictData(DictDataPageRequest request) {
        PageResult<SysDictDataDTO> page = dictService.pageDictData(toDictDataQuery(request));
        DictDataPageResponse response = new DictDataPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toDictDataRow).toList());
        return response;
    }

    /**
     * 按冻结自然年分页读取节假日日历最小安全投影。
     */
    public HolidayCalendarPageResponse pageHolidayCalendar(HolidayCalendarPageRequest request) {
        PageResult<CalendarDayResponse> page = holidayCalendarService.pageYearDays(
                request.getQuery().getYear(), request.getPageNo(), request.getPageSize());
        HolidayCalendarPageResponse response = new HolidayCalendarPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toHolidayCalendarRow).toList());
        return response;
    }

    /**
     * 按冻结条件分页读取任务运行日志，复用 Job 分页接口而不调用全量列表接口。
     *
     * @param request Data 内部分页请求
     * @return 任务运行日志安全投影分页
     */
    public JobRunLogPageResponse pageJobRunLogs(JobRunLogPageRequest request) {
        PageResult<JobRunLogResponse> page = jobSchedulerApplicationService.pageRunLogsForExport(toJobLogQuery(request));
        JobRunLogPageResponse response = new JobRunLogPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toJobRunLogRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围分页读取安全拦截事件最小安全投影。
     */
    public SecurityInterceptEventPageResponse pageSecurityInterceptEvents(
            SecurityInterceptEventPageRequest request) {
        PageResult<SecurityInterceptEventResponse> page = securityInterceptEventService.pageEvents(
                toSecurityInterceptEventQuery(request),
                dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SecurityInterceptEventPageResponse response = new SecurityInterceptEventPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream()
                .map(this::toSecurityInterceptEventRow)
                .toList());
        return response;
    }

    /** 按冻结条件分页读取系统账户，并裁剪为既有导出列。 */
    public SystemUserPageResponse pageSystemUsers(SystemUserPageRequest request) {
        PageResult<SysUserAccountDTO> page = userApplicationService.pageUsers(toSystemUserQuery(request));
        SystemUserPageResponse response = new SystemUserPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toSystemUserRow).toList());
        return response;
    }

    /** 按冻结条件分页读取系统参数，并在内部服务边界前完成值脱敏。 */
    public SystemConfigPageResponse pageSystemConfigs(SystemConfigPageRequest request) {
        PageResult<SysConfigDTO> page = configApplicationService.pageConfigs(toSystemConfigQuery(request));
        SystemConfigPageResponse response = new SystemConfigPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toSystemConfigRow).toList());
        return response;
    }

    /** 按冻结条件分页读取商户 IP 白名单，并保留既有按商户聚合语义。 */
    public MerchantIpWhitelistPageResponse pageMerchantIpWhitelists(
            MerchantIpWhitelistPageRequest request) {
        MerchantIpWhitelistQuery query = toMerchantIpWhitelistQuery(request);
        PageResult<MerchantIpWhitelistResponse> page =
                merchantIpWhitelistApplicationService.pageWhitelists(query);
        MerchantIpWhitelistPageResponse response = new MerchantIpWhitelistPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toMerchantIpWhitelistRow).toList());
        return response;
    }

    /**
     * 恢复卡 BIN 筛选条件，并以内部请求的分页参数覆盖页面分页。
     */
    private CardBinQueryRequest toCardBinQuery(CardBinPageRequest request) {
        var source = request.getQuery();
        CardBinQueryRequest target = new CardBinQueryRequest();
        target.setCardBin(source.getCardBin());
        target.setCardBrand(source.getCardBrand());
        target.setCardType(source.getCardType());
        target.setIssuerCountryAlpha2(source.getIssuerCountryAlpha2());
        target.setIssuerBank(source.getIssuerBank());
        target.setStatus(source.getStatus());
        target.setDataSource(source.getDataSource());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 将冻结 MCC 条件恢复为权威基础数据服务查询。 */
    private MccRequests.MccTreeQueryRequest toMccQuery(MccCodePageRequest request) {
        var source = request.getQuery();
        MccRequests.MccTreeQueryRequest target = new MccRequests.MccTreeQueryRequest();
        target.setKeyword(source.getKeyword());
        target.setMccCode(source.getMccCode());
        target.setNameCn(source.getNameCn());
        target.setNameEn(source.getNameEn());
        target.setNodeType(source.getNodeType());
        target.setRiskLevel(source.getRiskLevel());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 恢复任务日志公开筛选条件，并以内部请求的分页参数覆盖页面分页。
     */
    private JobRunLogQueryRequest toJobLogQuery(JobRunLogPageRequest request) {
        var source = request.getQuery();
        JobRunLogQueryRequest target = new JobRunLogQueryRequest();
        target.setJobId(source.getJobId());
        target.setJobCode(source.getJobCode());
        target.setRunStatus(source.getRunStatus());
        target.setTriggerType(source.getTriggerType());
        target.setExecutorNode(source.getExecutorNode());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复字典类型冻结条件，并以内部分页参数覆盖任何页面分页状态。
     */
    private SysDictTypeQueryRequest toDictTypeQuery(DictTypePageRequest request) {
        var source = request.getQuery();
        SysDictTypeQueryRequest target = new SysDictTypeQueryRequest();
        target.setDictName(source.getDictName());
        target.setDictType(source.getDictType());
        target.setBizDomain(source.getBizDomain());
        target.setStatus(source.getStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复字典数据冻结条件，并以内部分页参数覆盖任何页面分页状态。
     */
    private SysDictDataQueryRequest toDictDataQuery(DictDataPageRequest request) {
        var source = request.getQuery();
        SysDictDataQueryRequest target = new SysDictDataQueryRequest();
        target.setDictType(source.getDictType());
        target.setDictLabel(source.getDictLabel());
        target.setDictValue(source.getDictValue());
        target.setParentValue(source.getParentValue());
        target.setLocale(source.getLocale());
        target.setStatus(source.getStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复安全拦截事件冻结条件，并以内部分页参数覆盖页面分页。
     */
    private SecurityInterceptEventQuery toSecurityInterceptEventQuery(
            SecurityInterceptEventPageRequest request) {
        var source = request.getQuery();
        SecurityInterceptEventQuery target = new SecurityInterceptEventQuery();
        target.setEventNo(source.getEventNo());
        target.setSourceLayer(source.getSourceLayer());
        target.setEventType(source.getEventType());
        target.setRiskLevel(source.getRiskLevel());
        target.setAction(source.getAction());
        target.setMerchantId(source.getMerchantId());
        target.setClientIp(source.getClientIp());
        target.setRequestPath(source.getRequestPath());
        target.setTraceId(source.getTraceId());
        target.setRequestId(source.getRequestId());
        target.setHitRuleCode(source.getHitRuleCode());
        target.setProcessStatus(source.getProcessStatus());
        target.setQueryTimeZone(source.getQueryTimeZone());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复系统账户冻结条件，并以内部分页覆盖浏览器分页状态。 */
    private SysUserAccountQueryRequest toSystemUserQuery(SystemUserPageRequest request) {
        var source = request.getQuery();
        SysUserAccountQueryRequest target = new SysUserAccountQueryRequest();
        target.setLoginAccount(source.getLoginAccount());
        target.setMobile(source.getMobile());
        target.setEmail(source.getEmail());
        target.setDeptId(source.getDeptId());
        target.setStatus(source.getStatus());
        target.setEmailVerificationStatus(source.getEmailVerificationStatus());
        target.setActivationStatus(source.getActivationStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复系统参数冻结条件，并以内部分页覆盖浏览器分页状态。 */
    private SysConfigQueryRequest toSystemConfigQuery(SystemConfigPageRequest request) {
        var source = request.getQuery();
        SysConfigQueryRequest target = new SysConfigQueryRequest();
        target.setConfigName(source.getConfigName());
        target.setConfigKey(source.getConfigKey());
        target.setConfigGroup(source.getConfigGroup());
        target.setStatus(source.getStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 恢复商户 IP 白名单冻结条件，并强制执行可信商户范围。 */
    private MerchantIpWhitelistQuery toMerchantIpWhitelistQuery(
            MerchantIpWhitelistPageRequest request) {
        var source = request.getQuery();
        MerchantIpWhitelistQuery target = new MerchantIpWhitelistQuery();
        target.setMerchantId(source.getMerchantId());
        target.setMerchantKeyword(source.getMerchantKeyword());
        target.setIpValue(source.getIpValue());
        target.setIpType(source.getIpType());
        target.setStatus(source.getStatus());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setSubmitSource(source.getSubmitSource());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        if (!request.isAllMerchants()) {
            if (request.getMerchantIds() == null || request.getMerchantIds().isEmpty()) {
                target.setMerchantId("__NO_AUTHORIZED_MERCHANT__");
            } else if (target.getMerchantId() == null && request.getMerchantIds().size() == 1) {
                target.setMerchantId(request.getMerchantIds().iterator().next());
            } else if (target.getMerchantId() == null
                    || !request.getMerchantIds().contains(target.getMerchantId())) {
                target.setMerchantId("__NO_AUTHORIZED_MERCHANT__");
            }
        }
        return target;
    }

    /**
     * 将冻结商户范围恢复为显式全量或有限集合。
     */
    private AdminMerchantDataScope dataScope(boolean allMerchants,
                                             java.util.Set<String> merchantIds) {
        return allMerchants ? AdminMerchantDataScope.all()
                : AdminMerchantDataScope.limited(merchantIds);
    }

    /**
     * 将卡 BIN 页面结果裁剪为导出所需字段，保留已解析的字典展示值。
     */
    private CardBinRow toCardBinRow(CardBinResponse source) {
        CardBinRow row = new CardBinRow();
        row.setCardBinStart(source.getCardBinStart());
        row.setCardBinEnd(source.getCardBinEnd());
        row.setBinLength(source.getBinLength());
        row.setCardBrand(source.getCardBrandName());
        row.setCardSubBrand(source.getCardSubBrand());
        row.setCardType(source.getCardTypeName());
        row.setCardLevel(source.getCardLevel());
        row.setIssuerCountryName(source.getIssuerCountryName());
        row.setIssuerCountryAlpha2(source.getIssuerCountryAlpha2());
        row.setIssuerCountryAlpha3(source.getIssuerCountryAlpha3());
        row.setIssuerCountryNumeric(source.getIssuerCountryNumeric());
        row.setIssuerBank(source.getIssuerBank());
        row.setIssuerWebUrl(source.getIssuerWebUrl());
        row.setIssuerTelephone(source.getIssuerTelephone());
        row.setDataSource(source.getDataSourceName());
        row.setStatus(source.getStatusName());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setRemark(source.getRemark());
        row.setCreateBy(source.getCreateBy());
        row.setUpdateBy(source.getUpdateBy());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 将国家和地区页面结果裁剪为导出字段。 */
    private CountryRow toCountryRow(Response source) {
        CountryRow row = new CountryRow();
        row.setAlpha2Code(source.getAlpha2Code());
        row.setAlpha3Code(source.getAlpha3Code());
        row.setChineseName(source.getChineseName());
        row.setEnglishName(source.getEnglishName());
        row.setContinentName(source.getContinentName());
        row.setCurrencyAlpha3Code(source.getCurrencyAlpha3Code());
        row.setStatus(source.getStatus());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /** 将币种页面结果裁剪为导出字段并保持数字代码的文本语义。 */
    private CurrencyRow toCurrencyRow(
            com.scott.payment.admin.dto.base.IsoCurrencyDTOs.Response source) {
        CurrencyRow row = new CurrencyRow();
        row.setAlpha3Code(source.getAlpha3Code());
        row.setNumericCode(source.getNumericCode());
        row.setChineseName(source.getChineseName());
        row.setEnglishName(source.getEnglishName());
        row.setCurrencySymbol(source.getCurrencySymbol());
        row.setFractionDigits(source.getFractionDigits());
        row.setStatus(source.getStatus());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /** 将国家默认币种关系页面结果裁剪为导出字段。 */
    private RegionCurrencyRow toRegionCurrencyRow(RegionCurrencyResponse source) {
        RegionCurrencyRow row = new RegionCurrencyRow();
        row.setAlpha2Code(source.getAlpha2Code());
        row.setCountryName(source.getCountryName());
        row.setContinentName(source.getContinentName());
        row.setCurrencyAlpha3Code(source.getCurrencyAlpha3Code());
        row.setCurrencyName(source.getCurrencyName());
        row.setCurrencySymbol(source.getCurrencySymbol());
        row.setStatus(source.getStatus());
        return row;
    }

    /** 将 MCC 编码页面结果裁剪为导出字段。 */
    private MccCodeRow toMccCodeRow(MccVO.MccCodeVO source) {
        MccCodeRow row = new MccCodeRow();
        row.setMccCode(source.getMccCode());
        row.setNameCn(source.getNameCn());
        row.setNameEn(source.getNameEn());
        row.setLevel1Name(source.getLevel1Name());
        row.setLevel2Name(source.getLevel2Name());
        row.setMccType(source.getMccType());
        row.setRiskLevel(source.getRiskLevel());
        row.setDeliveryApplicability(source.getDeliveryApplicability());
        row.setStatus(source.getStatus());
        row.setSource(source.getSource());
        row.setVersionNo(source.getVersionNo());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setRemark(source.getRemark());
        return row;
    }

    /**
     * 将部门实体裁剪为与既有同步导出一致的安全字段。
     */
    private DepartmentRow toDepartmentRow(SysDeptDO source, Map<Long, String> departmentNames) {
        DepartmentRow row = new DepartmentRow();
        row.setDeptName(source.getDeptName());
        row.setParentName(source.getParentId() == null || source.getParentId() == 0
                ? "-" : departmentNames.getOrDefault(source.getParentId(), "-"));
        row.setSortNo(source.getSortNo());
        row.setLeader(source.getLeader());
        row.setPhone(source.getPhone());
        row.setEmail(source.getEmail());
        row.setStatus(source.getStatus());
        return row;
    }

    /**
     * 将岗位实体裁剪为导出安全字段，并保留岗位编码文本语义。
     */
    private PostRow toPostRow(SysPostDO source) {
        PostRow row = new PostRow();
        row.setPostCode(source.getPostCode());
        row.setPostName(source.getPostName());
        row.setSortNo(source.getSortNo());
        row.setStatus(source.getStatus());
        row.setRemark(source.getRemark());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /**
     * 将字典类型裁剪为导出安全字段，不传播内部主键和编辑控制字段。
     */
    private DictTypeRow toDictTypeRow(SysDictTypeDTO source) {
        DictTypeRow row = new DictTypeRow();
        row.setDictName(source.getDictName());
        row.setDictType(source.getDictType());
        row.setBizDomain(source.getBizDomain());
        row.setStatus(source.getStatus());
        row.setSystemBuiltin(source.getSystemBuiltin());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /**
     * 将字典数据裁剪为导出安全字段，不传播扩展 JSON 和内部主键。
     */
    private DictDataRow toDictDataRow(SysDictDataDTO source) {
        DictDataRow row = new DictDataRow();
        row.setDictType(source.getDictType());
        row.setDictLabel(source.getDictLabel());
        row.setDictValue(source.getDictValue());
        row.setLocale(source.getLocale());
        row.setDictSort(source.getDictSort());
        row.setStatus(source.getStatus());
        row.setDefaultFlag(source.getIsDefault());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /**
     * 将节假日日历日期裁剪为导出安全字段，保留必要审计更新时间。
     */
    private HolidayCalendarRow toHolidayCalendarRow(CalendarDayResponse source) {
        HolidayCalendarRow row = new HolidayCalendarRow();
        row.setCalendarDate(source.getCalendarDate());
        row.setDayOfWeek(source.getDayOfWeek());
        row.setDayType(source.getDayType());
        row.setHolidayName(source.getHolidayName());
        row.setStatutoryHoliday(source.getStatutoryHoliday());
        row.setAdjustedWorkday(source.getAdjustedWorkday());
        row.setRemark(source.getRemark());
        row.setUpdateBy(source.getUpdateBy());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /**
     * 将任务日志裁剪为运营导出字段，明确排除参数快照和完整执行结果。
     */
    private JobRunLogRow toJobRunLogRow(JobRunLogResponse source) {
        JobRunLogRow row = new JobRunLogRow();
        row.setRunId(source.getRunId());
        row.setJobCode(source.getJobCode());
        row.setJobName(source.getJobName());
        row.setTriggerType(source.getTriggerType());
        row.setRunStatus(source.getRunStatus());
        row.setExecutorNode(source.getExecutorNode());
        row.setStartTime(source.getStartTime());
        row.setEndTime(source.getEndTime());
        row.setDurationMs(source.getDurationMs());
        row.setErrorMessage(source.getErrorMessage());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 裁剪安全事件导出字段，明确排除请求头、User-Agent、请求号和原始载荷。
     */
    private SecurityInterceptEventRow toSecurityInterceptEventRow(
            SecurityInterceptEventResponse source) {
        SecurityInterceptEventRow row = new SecurityInterceptEventRow();
        row.setEventNo(source.getEventNo());
        row.setEventTime(source.getEventTime());
        row.setSourceLayer(source.getSourceLayer());
        row.setEventType(source.getEventType());
        row.setRiskLevel(source.getRiskLevel());
        row.setAction(source.getAction());
        row.setMerchantId(source.getMerchantId());
        row.setClientIp(source.getClientIp());
        row.setRequestPath(source.getRequestPath());
        row.setTraceId(source.getTraceId());
        row.setHitRuleCode(source.getHitRuleCode());
        row.setReasonCode(source.getReasonCode());
        row.setReasonMessage(source.getReasonMessage());
        row.setProcessStatus(source.getProcessStatus());
        row.setProcessedBy(source.getProcessedBy());
        row.setProcessedTime(source.getProcessedTime());
        return row;
    }

    /** 将系统账户裁剪为既有 Excel 列使用的安全字段。 */
    private SystemUserRow toSystemUserRow(SysUserAccountDTO source) {
        SystemUserRow row = new SystemUserRow();
        row.setLoginAccount(source.getLoginAccount());
        row.setRealName(source.getRealName());
        row.setDeptName(source.getDeptName());
        row.setPostNames(source.getPostNames());
        row.setRoleNames(source.getRoleNames());
        row.setMobile(source.getMobile());
        row.setEmail(source.getEmail());
        row.setStatus(source.getStatus());
        row.setLocked(source.getLocked());
        row.setLastLoginAt(source.getLastLoginAt());
        row.setLastLoginIp(source.getLastLoginIp());
        row.setRemark(source.getRemark());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /** 将系统参数裁剪为导出字段，敏感原值不会跨越当前服务边界。 */
    private SystemConfigRow toSystemConfigRow(SysConfigDTO source) {
        SystemConfigRow row = new SystemConfigRow();
        row.setConfigName(source.getConfigName());
        row.setConfigKey(source.getConfigKey());
        row.setConfigGroup(source.getConfigGroup());
        row.setConfigValue(configExportValuePolicy.exportValue(source));
        row.setStatus(source.getStatus());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }

    /** 将按商户聚合的白名单结果裁剪为既有导出字段。 */
    private MerchantIpWhitelistRow toMerchantIpWhitelistRow(MerchantIpWhitelistResponse source) {
        MerchantIpWhitelistRow row = new MerchantIpWhitelistRow();
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setMerchantShortName(source.getMerchantShortName());
        row.setIpWhitelists(source.getIpWhitelists() == null ? java.util.List.of()
                : source.getIpWhitelists().stream().map(this::toMerchantIpWhitelistItemRow).toList());
        row.setUpdateBy(source.getUpdateBy());
        row.setUpdateTime(source.getGmtModified());
        return row;
    }

    /** 裁剪单个 IP 白名单明细，不传播审批意见等内部字段。 */
    private MerchantIpWhitelistItemRow toMerchantIpWhitelistItemRow(MerchantIpWhitelistItem source) {
        MerchantIpWhitelistItemRow row = new MerchantIpWhitelistItemRow();
        row.setIpType(source.getIpType());
        row.setIpValue(source.getIpValue());
        row.setStatus(source.getStatus());
        return row;
    }
}

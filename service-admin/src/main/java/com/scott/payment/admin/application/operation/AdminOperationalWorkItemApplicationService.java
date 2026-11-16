package com.scott.payment.admin.application.operation;

import com.scott.payment.admin.dto.export.OperationalWorkItemExportRow;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.ActionRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.AssigneeOption;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SyncResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemDetail;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemHistory;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemSummary;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.PageRequest;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.PageResponse;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.Row;
import com.scott.payment.admin.dto.exporttask.OperationalWorkItemExportInternalDTOs.WorkItemQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminOperationalWorkItemService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 管理端统一运营待办应用服务，负责可信身份、数据范围和导出编排。
 */
@Service
public class AdminOperationalWorkItemApplicationService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminOperationalWorkItemService workItemService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建只编排管理端用例的统一待办应用服务。
     *
     * @param workItemService 统一待办领域服务
     * @param dataScopeResolver 管理端商户数据范围解析器
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化消息解析器
     * @param excelLocaleResolver Excel 语言环境解析器
     */
    public AdminOperationalWorkItemApplicationService(AdminOperationalWorkItemService workItemService,
                                                      AdminMerchantDataScopeResolver dataScopeResolver,
                                                      ExcelExportService excelExportService,
                                                      ExcelI18nMessageResolver excelI18nMessageResolver,
                                                      ExcelLocaleResolver excelLocaleResolver) {
        this.workItemService = workItemService;
        this.dataScopeResolver = dataScopeResolver;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 查询当前账号可访问的统一待办。
     *
     * @param request 查询条件
     * @return 待办分页及汇总
     */
    public SearchResponse search(SearchRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.search(request, dataScope(account), account.getAccountId());
    }

    /**
     * 查询待办实时来源摘要和运营处理轨迹。
     *
     * @param workItemNo 待办编号
     * @return 待办详情
     */
    public WorkItemDetail detail(String workItemNo) {
        InternalAuthAccount account = currentAccount();
        return workItemService.detail(workItemNo, dataScope(account));
    }

    /**
     * 分页查询待办处理轨迹，并复用详情的数据范围校验。
     *
     * @param workItemNo 待办编号
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 处理轨迹分页
     */
    public PageResult<WorkItemHistory> searchHistory(String workItemNo,
                                                     Integer pageNo,
                                                     Integer pageSize) {
        InternalAuthAccount account = currentAccount();
        return workItemService.searchHistory(workItemNo, pageNo, pageSize, dataScope(account));
    }

    /**
     * 当前操作人认领未分派待办。
     *
     * @param workItemNo 待办编号
     * @param request 操作命令
     * @return 更新后的待办详情
     */
    public WorkItemDetail claim(String workItemNo, ActionRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.claim(workItemNo, request, account.getAccountId(), operatorName(account),
                dataScope(account));
    }

    /**
     * 将待办分派给有效管理账号。
     *
     * @param workItemNo 待办编号
     * @param request 操作命令
     * @return 更新后的待办详情
     */
    public WorkItemDetail assign(String workItemNo, ActionRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.assign(workItemNo, request, account.getAccountId(), operatorName(account),
                account.getAppId(), dataScope(account));
    }

    /**
     * 将已分派待办转交给其他有效管理账号。
     *
     * @param workItemNo 待办编号
     * @param request 操作命令
     * @return 更新后的待办详情
     */
    public WorkItemDetail transfer(String workItemNo, ActionRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.transfer(workItemNo, request, account.getAccountId(), operatorName(account),
                account.getAppId(), dataScope(account));
    }

    /**
     * 以必填处理结论完成人工待办，并保留来源业务事实。
     *
     * @param workItemNo 待办编号
     * @param request 操作命令
     * @return 更新后的待办详情
     */
    public WorkItemDetail complete(String workItemNo, ActionRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.complete(workItemNo, request, account.getAccountId(), operatorName(account),
                dataScope(account));
    }

    /**
     * 追加待办处理备注，不覆盖既有轨迹。
     *
     * @param workItemNo 待办编号
     * @param request 操作命令
     * @return 更新后的待办详情
     */
    public WorkItemDetail remark(String workItemNo, ActionRequest request) {
        InternalAuthAccount account = currentAccount();
        return workItemService.remark(workItemNo, request, account.getAccountId(), operatorName(account),
                dataScope(account));
    }

    /**
     * 查询同一 ADMIN 应用内可接收待办的账号。
     *
     * @param keyword 登录账号或人员名称关键字
     * @return 可分派账号选项
     */
    public List<AssigneeOption> assignees(String keyword) {
        return workItemService.listAssignees(currentAccount().getAppId(), keyword);
    }

    /**
     * 导出当前筛选条件下、当前账号有权查看的待办。
     *
     * @param request 导出筛选条件
     * @param response HTTP 响应
     */
    public void export(SearchRequest request, HttpServletResponse response) {
        InternalAuthAccount account = currentAccount();
        List<OperationalWorkItemExportRow> rows = workItemService.listForExport(
                        request, dataScope(account), account.getAccountId())
                .stream().map(this::toExportRow).toList();
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String titleKey = "excel.operations.workItem.title";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.export(ExcelExportRequest.<OperationalWorkItemExportRow>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(operatorName(account))
                .exportTime(now)
                .locale(locale)
                .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                .rowClass(OperationalWorkItemExportRow.class)
                .dataList(rows)
                .build(), response);
    }

    /**
     * 内部任务调用的六类来源幂等同步入口。
     *
     * @param request 同步命令
     * @return 本轮来源扫描和状态变更统计
     */
    public SyncResponse synchronize(SyncRequest request) {
        return workItemService.synchronize(request);
    }

    /**
     * 使用可信冻结范围读取一页待办导出数据，内部执行器不能扩大商户集合。
     *
     * @param request Data 保存的任务快照和分页参数
     * @return 一页权威待办摘要
     */
    public PageResponse pageForExport(PageRequest request) {
        AdminMerchantDataScope scope = request.isAllMerchants()
                ? AdminMerchantDataScope.all() : AdminMerchantDataScope.limited(request.getMerchantIds());
        PageResult<WorkItemSummary> page = workItemService.pageForExport(
                toSearchRequest(request.getQuery(), request.getPageNo(), request.getPageSize()),
                scope, request.getCreatorAccountId());
        PageResponse response = new PageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toInternalRow).toList());
        return response;
    }

    private AdminMerchantDataScope dataScope(InternalAuthAccount account) {
        return dataScopeResolver.resolve(account);
    }

    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || account.getAppId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    private String operatorName(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName().trim();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "admin";
    }

    private OperationalWorkItemExportRow toExportRow(WorkItemSummary source) {
        OperationalWorkItemExportRow row = new OperationalWorkItemExportRow();
        row.setWorkItemNo(source.getWorkItemNo());
        row.setSourceType(source.getSourceType());
        row.setBusinessNo(source.getBusinessNo());
        row.setMerchantId(source.getMerchantId());
        row.setWorkStatus(source.getWorkStatus());
        row.setSourceStatus(source.getSourceStatus());
        row.setPriority(source.getPriority());
        row.setOwnerAccountName(source.getOwnerAccountName());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setFirstDetectedAt(source.getFirstDetectedAt());
        row.setSlaDueAt(source.getSlaDueAt());
        row.setOverdue(Boolean.TRUE.equals(source.getOverdue()) ? "YES" : "NO");
        row.setLatestRemark(source.getLatestRemark());
        return row;
    }

    /**
     * 将冻结查询恢复为既有领域查询对象，分页参数只接受内部接口校验后的值。
     */
    private SearchRequest toSearchRequest(WorkItemQuery source, Integer pageNo, Integer pageSize) {
        SearchRequest target = new SearchRequest();
        target.setWorkItemNo(source.getWorkItemNo());
        target.setKeyword(source.getKeyword());
        target.setSourceType(source.getSourceType());
        target.setWorkStatus(source.getWorkStatus());
        target.setPriority(source.getPriority());
        target.setOwnerAccountId(source.getOwnerAccountId());
        target.setMineOnly(source.getMineOnly());
        target.setUnassignedOnly(source.getUnassignedOnly());
        target.setOverdueOnly(source.getOverdueOnly());
        target.setDetectedStartTime(source.getDetectedStartTime());
        target.setDetectedEndTime(source.getDetectedEndTime());
        target.setPageNo(pageNo);
        target.setPageSize(pageSize);
        return target;
    }

    /**
     * 投影异步导出需要的稳定摘要，不向 Data 传递来源敏感诊断字段。
     */
    private Row toInternalRow(WorkItemSummary source) {
        Row row = new Row();
        row.setWorkItemNo(source.getWorkItemNo());
        row.setSourceType(source.getSourceType());
        row.setBusinessNo(source.getBusinessNo());
        row.setMerchantId(source.getMerchantId());
        row.setWorkStatus(source.getWorkStatus());
        row.setSourceStatus(source.getSourceStatus());
        row.setPriority(source.getPriority());
        row.setOwnerAccountName(source.getOwnerAccountName());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setFirstDetectedAt(source.getFirstDetectedAt());
        row.setSlaDueAt(source.getSlaDueAt());
        row.setOverdue(source.getOverdue());
        row.setLatestRemark(source.getLatestRemark());
        return row;
    }
}

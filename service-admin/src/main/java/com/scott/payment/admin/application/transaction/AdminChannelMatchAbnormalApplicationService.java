package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalRecord;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignClientCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignRequest;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.BatchRequeryResult;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.RequeryCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.ResolveCommand;
import com.scott.payment.admin.dto.export.ChannelMatchAbnormalExportRow;
import com.scott.payment.admin.service.AdminChannelMatchAbnormalQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelMatchAbnormalApplicationService
 * @date : 2026-08-06 00:00
 * @description : 管理端勾兑异常应用服务，本地编排案件查询和导出，仅将领取、重查及关闭命令提交给 service-payment。
 * @status : create
 */
@Service
public class AdminChannelMatchAbnormalApplicationService {

    private static final String ADMIN_ACCOUNT_PREFIX = "admin-account:";
    private static final int EXPORT_PAGE_SIZE = 500;
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminChannelMatchAbnormalQueryService abnormalQueryService;
    private final PaymentInternalClient paymentInternalClient;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;
    private final TransactionShardingProperties shardingProperties;
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /**
     * 创建管理端勾兑异常应用服务。
     *
     * @param abnormalQueryService service-admin 本地勾兑异常查询服务
     * @param paymentInternalClient service-payment 案件处置命令客户端
     * @param excelExportService Excel 分页导出服务
     * @param excelI18nMessageResolver Excel 国际化消息解析器
     * @param excelLocaleResolver Excel 语言环境解析器
     * @param shardingProperties 交易查询预算配置
     * @param exportConcurrencyLimiter 导出并发限制器
     */
    public AdminChannelMatchAbnormalApplicationService(AdminChannelMatchAbnormalQueryService abnormalQueryService,
                                                       PaymentInternalClient paymentInternalClient,
                                                       ExcelExportService excelExportService,
                                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                                       ExcelLocaleResolver excelLocaleResolver,
                                                       TransactionShardingProperties shardingProperties,
                                                       RedisConcurrencyLimiter exportConcurrencyLimiter) {
        this.abnormalQueryService = abnormalQueryService;
        this.paymentInternalClient = paymentInternalClient;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.shardingProperties = shardingProperties;
        this.exportConcurrencyLimiter = exportConcurrencyLimiter;
    }

    /**
     * 查询勾兑异常案件分页及统计。
     *
     * @param query 案件筛选、时间范围和分页条件
     * @return 案件分页和状态统计
     */
    public AbnormalSearchResponse search(AbnormalQuery query) {
        return abnormalQueryService.search(query);
    }

    /**
     * 查询勾兑异常案件聚合详情。
     *
     * @param eventId 勾兑异常案件号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 案件记录和交易生命周期详情
     */
    public AbnormalDetailResponse detail(String eventId, LocalDateTime transactionDateTime) {
        return abnormalQueryService.detail(eventId, transactionDateTime);
    }

    /**
     * 按查询条件分页流式导出脱敏案件记录。
     *
     * @param query 案件筛选条件
     * @param response 文件下载响应
     */
    public void export(AbnormalQuery query, HttpServletResponse response) {
        InternalAuthAccount account = currentAccount();
        boolean acquired = exportConcurrencyLimiter.execute(
                "transaction", "admin-match-abnormal-export", ADMIN_ACCOUNT_PREFIX + account.getAccountId(),
                shardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser(),
                EXPORT_LEASE_TIME,
                () -> exportExcel(query == null ? new AbnormalQuery() : query, displayName(account), response));
        if (!acquired) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 提交案件领取或转派命令。
     *
     * @param eventId 勾兑异常案件号
     * @param request 目标账号、真实分片时间和期望版本
     * @return 支付核心返回的最新案件记录
     */
    public AbnormalRecord assign(String eventId, AssignRequest request) {
        if (request == null || request.getTransactionDateTime() == null || request.getExpectedVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount current = currentAccount();
        String targetAccountId = StringUtils.hasText(request.getAssigneeAccountId())
                ? request.getAssigneeAccountId() : String.valueOf(current.getAccountId());
        AssignClientCommand command = new AssignClientCommand();
        command.setTransactionDateTime(request.getTransactionDateTime());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setOperatorId(targetAccountId.startsWith(ADMIN_ACCOUNT_PREFIX)
                ? targetAccountId : ADMIN_ACCOUNT_PREFIX + targetAccountId);
        command.setOperatorName(StringUtils.hasText(request.getAssigneeName())
                ? request.getAssigneeName() : displayName(current));
        return paymentInternalClient.assignChannelMatchAbnormality(eventId, command);
    }

    /**
     * 提交单笔重新勾兑命令。
     *
     * @param eventId 勾兑异常案件号
     * @param command 真实分片时间和期望版本
     * @return 支付核心返回的最新案件记录
     */
    public AbnormalRecord requery(String eventId, RequeryCommand command) {
        currentAccount();
        return paymentInternalClient.requeryChannelMatchAbnormality(eventId, command);
    }

    /**
     * 提交批量重新勾兑命令。
     *
     * @param command 待重查案件及其真实分片时间、期望版本
     * @return 批量受理和失败统计
     */
    public BatchRequeryResult batchRequery(BatchRequeryCommand command) {
        currentAccount();
        return paymentInternalClient.batchRequeryChannelMatchAbnormalities(command);
    }

    /**
     * 提交关闭或忽略案件命令，不允许指定交易目标状态。
     *
     * @param eventId 勾兑异常案件号
     * @param command 处置类型、原因、真实分片时间和期望版本
     * @return 支付核心返回的最新案件记录
     */
    public AbnormalRecord resolve(String eventId, ResolveCommand command) {
        currentAccount();
        return paymentInternalClient.resolveChannelMatchAbnormality(eventId, command);
    }

    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    private String displayName(InternalAuthAccount account) {
        return StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
    }

    private void exportExcel(AbnormalQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String titleKey = "excel.abnormal.title";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(
                ExcelPagedExportRequest.<ChannelMatchAbnormalExportRow>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary("beginTime=" + query.getBeginTime() + ", endTime=" + query.getEndTime()
                                + ", status=" + query.getEventStatus() + ", channel=" + query.getChannelCode())
                        .rowClass(ChannelMatchAbnormalExportRow.class)
                        .pageSize(EXPORT_PAGE_SIZE)
                        .pageLoader(pageNo -> loadExportPage(query, pageNo))
                        .build(), response);
    }

    private List<ChannelMatchAbnormalExportRow> loadExportPage(AbnormalQuery query, int pageNo) {
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        AbnormalSearchResponse searchResponse = abnormalQueryService.search(query);
        if (searchResponse == null || searchResponse.getPage() == null) {
            return List.of();
        }
        return searchResponse.getPage().getRecords().stream().map(this::toExportRow).toList();
    }

    private ChannelMatchAbnormalExportRow toExportRow(AbnormalRecord source) {
        ChannelMatchAbnormalExportRow row = new ChannelMatchAbnormalExportRow();
        row.setAbnormalEventId(source.getAbnormalEventId());
        row.setTransactionId(source.getTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setAbnormalType(source.getAbnormalType());
        row.setAbnormalLevel(source.getAbnormalLevel());
        row.setEventStatus(source.getEventStatus());
        row.setPlatformStatus(source.getPlatformStatus());
        row.setChannelCode(source.getChannelCode());
        row.setChannelStatus(source.getChannelStatus());
        row.setPlatformAmount(source.getPlatformAmount());
        row.setPlatformCurrency(source.getPlatformCurrency());
        row.setOccurrenceCount(source.getOccurrenceCount());
        row.setAssignedToName(source.getAssignedToName());
        row.setFirstSeenTime(source.getFirstSeenTime());
        row.setLastSeenTime(source.getLastSeenTime());
        return row;
    }
}

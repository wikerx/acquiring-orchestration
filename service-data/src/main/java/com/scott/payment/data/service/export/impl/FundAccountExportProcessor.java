package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundAccountQuery;
import com.scott.payment.data.client.admin.AdminFundExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.FundAccountExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FUND_ACCOUNT;

/**
 * 管理端资金账户异步导出处理器，按冻结商户范围分页生成私有 Excel 文件。
 */
@Service
public class FundAccountExportProcessor implements ExportTaskProcessor {
    private final AdminFundExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建资金账户异步导出处理器。 */
    public FundAccountExportProcessor(AdminFundExportSourceClient sourceClient,
                                      ExcelExportArtifactWriter artifactWriter,
                                      ExcelI18nMessageResolver messageResolver,
                                      DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回资金账户稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_FUND_ACCOUNT;
    }

    /** 解析冻结条件和范围并生成资金账户文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task, ExportObjectStorageService storage,
                                  ExportProgressListener progressListener, LocalDateTime now) {
        FundAccountQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), FundAccountQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.fund.title", "资金账户", "Fund Accounts",
                FundAccountExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 读取一页资金账户并本地化固定枚举。 */
    private ExportPage<FundAccountExportRow> loadPage(
            DataExportTaskDO task, FundAccountQuery query, AdminExportScopeSnapshot scope,
            int pageNo, Locale locale) {
        FundAccountPageRequest request = new FundAccountPageRequest();
        populateScope(request, task, scope, query, pageNo);
        FundAccountPageResponse response = sourceClient.loadFundAccountPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FundAccountExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 填充任务创建人、冻结范围和服务端分页。 */
    private void populateScope(FundAccountPageRequest request, DataExportTaskDO task,
                               AdminExportScopeSnapshot scope, FundAccountQuery query, int pageNo) {
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
    }

    /** 保持金额和币种不变，仅转换账户状态与限制标识展示值。 */
    private FundAccountExportRow toExportRow(FundAccountRow source, Locale locale) {
        FundAccountExportRow row = new FundAccountExportRow();
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setSettlementCurrency(source.getSettlementCurrency());
        row.setAvailableBalance(source.getAvailableBalance());
        row.setAccountStatus(label("accountStatus", source.getAccountStatus(), locale));
        row.setReverseRestricted(label("reverseRestricted",
                source.getReverseRestricted() == null ? null : String.valueOf(source.getReverseRestricted()), locale));
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 解析资金枚举展示值，缺少翻译时保留协议原值。 */
    private String label(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.fund.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /** 解析任务冻结语言，未知语言回退简体中文。 */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}

package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundRechargeQuery;
import com.scott.payment.data.client.admin.AdminFundExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.FundRechargeExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FUND_RECHARGE;

/**
 * 管理端充值申请异步导出处理器，保留申请金额和审批链路事实。
 */
@Service
public class FundRechargeExportProcessor implements ExportTaskProcessor {
    private final AdminFundExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建充值申请异步导出处理器。 */
    public FundRechargeExportProcessor(AdminFundExportSourceClient sourceClient,
                                       ExcelExportArtifactWriter artifactWriter,
                                       ExcelI18nMessageResolver messageResolver,
                                       DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回充值申请稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_FUND_RECHARGE;
    }

    /** 解析冻结条件并生成充值申请文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task, ExportObjectStorageService storage,
                                  ExportProgressListener progressListener, LocalDateTime now) {
        FundRechargeQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), FundRechargeQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.fund.rechargeTitle", "充值申请", "Account Recharges",
                FundRechargeExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 读取一页充值申请并转换为冻结语言下的 Excel 行。 */
    private ExportPage<FundRechargeExportRow> loadPage(
            DataExportTaskDO task, FundRechargeQuery query, AdminExportScopeSnapshot scope,
            int pageNo, Locale locale) {
        FundRechargePageRequest request = new FundRechargePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        FundRechargePageResponse response = sourceClient.loadFundRechargePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FundRechargeExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 保持金额与币种不变，仅本地化申请状态。 */
    private FundRechargeExportRow toExportRow(FundRechargeRow source, Locale locale) {
        FundRechargeExportRow row = new FundRechargeExportRow();
        row.setRechargeNo(source.getRechargeNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setAccountNo(source.getAccountNo());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setRechargeStatus(label("rechargeStatus", source.getRechargeStatus(), locale));
        row.setRemark(source.getRemark());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        row.setAuditByName(source.getAuditByName());
        row.setAuditTime(source.getAuditTime());
        row.setRecheckByName(source.getRecheckByName());
        row.setRecheckTime(source.getRecheckTime());
        row.setLedgerNo(source.getLedgerNo());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /** 解析充值枚举展示值，缺少翻译时保留协议原值。 */
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

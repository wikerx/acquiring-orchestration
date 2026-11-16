package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantFundLedgerQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantFundLedgerExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_FUND_LEDGER;

/**
 * 商户余额流水异步导出处理器，按冻结商户和账户读取不可变账务事实。
 */
@Service
public class MerchantFundLedgerExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建商户余额流水异步导出处理器。
     *
     * @param sourceClient Merchant 安全导出来源客户端
     * @param artifactWriter Excel 文件生成器
     * @param properties 导出分页和容量配置
     */
    public MerchantFundLedgerExportProcessor(MerchantExportSourceClient sourceClient,
                                             ExcelExportArtifactWriter artifactWriter,
                                             DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回商户余额流水任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_FUND_LEDGER;
    }

    /**
     * 解析冻结快照并分页生成商户余额流水文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantFundLedgerQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantFundLedgerQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        requireScope(task, query, scope);
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.fund.ledgerTitle", "余额流水", "Balance Ledger",
                MerchantFundLedgerExportRow.class,
                pageNo -> loadPage(task, query, pageNo));
    }

    /**
     * 读取一页余额流水安全投影并复制为 Excel 行。
     */
    private ExportPage<MerchantFundLedgerExportRow> loadPage(DataExportTaskDO task,
                                                              MerchantFundLedgerQuery query,
                                                              int pageNo) {
        FundLedgerPageRequest request = new FundLedgerPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        FundLedgerPageResponse response = sourceClient.loadFundLedgerPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant fund ledger source response is missing");
        }
        List<MerchantFundLedgerExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制余额流水金额、币种和前后余额原值，不执行换算或舍入。
     */
    private MerchantFundLedgerExportRow toExportRow(FundLedgerRow source) {
        MerchantFundLedgerExportRow row = new MerchantFundLedgerExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setBusinessType(source.getBusinessType());
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(source.getBalanceType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 交叉校验任务商户范围、账户主键和查询快照，异常时在读取来源前失败关闭。
     */
    private void requireScope(DataExportTaskDO task,
                              MerchantFundLedgerQuery query,
                              MerchantExportScopeSnapshot scope) {
        if (query == null || query.getAccountId() == null || scope == null
                || !StringUtils.hasText(task.getMerchantId())
                || !task.getMerchantId().equals(scope.getMerchantId())) {
            throw new IllegalStateException("merchant fund ledger export task scope is invalid");
        }
    }
}

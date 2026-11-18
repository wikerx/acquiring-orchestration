package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PaymentLinkQuery;
import com.scott.payment.data.client.admin.AdminPaymentLinkExportSourceClient;
import com.scott.payment.data.client.paymentlink.PaymentLinkExportDTOs.*;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * Admin 支付链接异步文件处理器，只分页读取白名单字段并复用统一安全写入器。
 */
@Service
public class AdminPaymentLinkExportProcessor implements ExportTaskProcessor {
    private final AdminPaymentLinkExportSourceClient source;
    private final ExcelExportArtifactWriter writer;
    private final com.scott.payment.component.excel.support.ExcelI18nMessageResolver messages;

    /**
     * 复用已签名来源客户端和统一 Excel 分页写入/上传能力。
     */
    public AdminPaymentLinkExportProcessor(AdminPaymentLinkExportSourceClient source,ExcelExportArtifactWriter writer, com.scott.payment.component.excel.support.ExcelI18nMessageResolver messages) { this.source=source; this.writer=writer; this.messages=messages; }

    /**
     * 返回与账号系统绑定的稳定任务类型。
     */
    @Override public String taskType() { return "ADMIN_PAYMENT_LINK"; }

    /**
     * 解析任务冻结条件，保持商户范围、币种和金额精度，分页生成私有文件。
     */
    @Override public ExportArtifact process(DataExportTaskDO task,ExportObjectStorageService storage,
                                             ExportProgressListener progress,LocalDateTime now) {
        PaymentLinkQuery query=JsonUtils.parseObject(task.getQuerySnapshot(),PaymentLinkQuery.class);
        if(query==null || !"ADMIN".equals(task.getCreatorAppCode())) { throw new IllegalStateException("payment link task owner mismatch"); }
        return writer.write(task,storage,progress,now,"excel.paymentLink.title","支付链接","Payment Links",
                PaymentLinkExportRow.class,100,pageNo->loadPage(task,query,pageNo));
    }

    /**
     * 每次最多读取 100 条，不向付款人接口请求数据，也不返回私有分享地址。
     */
    private ExportPage<PaymentLinkExportRow> loadPage(DataExportTaskDO task,PaymentLinkQuery query,int pageNo) {
        PageRequest request=new PageRequest(); request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query); request.setPageNo(pageNo); request.setPageSize(100);
        AdminExportScopeSnapshot scope=JsonUtils.parseObject(task.getDataScopeSnapshot(),AdminExportScopeSnapshot.class);
        if(scope==null) { throw new IllegalStateException("payment link scope snapshot is missing"); }
        request.setAllMerchants(scope.isAllMerchants()); request.setMerchantIds(scope.getMerchantIds());
        var page=source.loadPaymentLinkPage(request);
        if(page==null) { throw new IllegalStateException("payment link export page is missing"); }
        return new ExportPage<>(page.getTotal(),page.getRecords().stream().map(row -> row(row,task)).toList());
    }

    /**
     * 显式复制白名单列，避免上游新增字段自动进入文件。
     */
    private PaymentLinkExportRow row(Row source,DataExportTaskDO task) { PaymentLinkExportRow row=new PaymentLinkExportRow();
        row.setLinkId(source.getLinkId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setLinkName(source.getLinkName());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setStatus(label("status",source.getStatus(),task));
        row.setPaymentStatus(label("payment",source.getPaymentStatus(),task));
        row.setExpireTime(displayTime(source.getExpireTime(),task));
        row.setCreateTime(displayTime(source.getCreateTime(),task));
        row.setPaidTime(displayTime(source.getPaidTime(),task));
        row.setCreatedAppCode(source.getCreatedAppCode());
        row.setCreatedBy(source.getCreatedBy());
        return row;
    }
    /**
     * 来源时间固定为上海时间，按任务冻结的显示时区转换；不依赖执行节点默认时区。
     */
    private LocalDateTime displayTime(LocalDateTime value,DataExportTaskDO task) {
        if(value==null) return null;
        java.time.ZoneId target=java.time.ZoneId.of(task.getTimezone()==null || task.getTimezone().isBlank()?"Asia/Shanghai":task.getTimezone());
        return value.atZone(java.time.ZoneId.of("Asia/Shanghai")).withZoneSameInstant(target).toLocalDateTime();
    }
    /**
     * 已知状态按任务语言显示；未知后端枚举原样保留，不转换为其他业务状态。
     */
    private String label(String group,String code,DataExportTaskDO task) {
        if(code==null) return "";
        String key="excel.paymentLink."+group+"."+code;
        String value=messages.resolve(key,java.util.Locale.forLanguageTag(task.getLocale()==null?"zh-CN":task.getLocale()));
        return value==null || key.equals(value)?code:value;
    }
}

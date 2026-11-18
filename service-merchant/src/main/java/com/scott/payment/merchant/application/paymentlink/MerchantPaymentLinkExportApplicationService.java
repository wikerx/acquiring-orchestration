package com.scott.payment.merchant.application.paymentlink;

import com.scott.payment.merchant.client.data.MerchantDataExportTaskClient;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.*;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/** 冻结当前账号、商户范围与允许的筛选字段后提交已有异步导出基础设施。 */
@Service
public class MerchantPaymentLinkExportApplicationService {
    private final MerchantDataExportTaskClient data;
    private final MerchantPaymentLinkApplicationService links;
    public MerchantPaymentLinkExportApplicationService(MerchantDataExportTaskClient data,MerchantPaymentLinkApplicationService links) { this.data=data; this.links=links; }
    /**
     * 冻结操作者范围和页面显示时区；旧调用缺省沿用上海时间，非法时区拒绝创建任务。
     */
    public TaskSummary submit(String requestId,PaymentLinkDTOs.Query input,String timezone) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_.:-]{1,80}")) { throw new ServiceException(ApiResultEnum.PARAM_INVALID); }
        String frozenTimezone;
        try { frozenTimezone=java.time.ZoneId.of(timezone==null || timezone.isBlank()?"Asia/Shanghai":timezone).getId(); }
        catch(java.time.DateTimeException invalidTimezone) { throw new ServiceException(ApiResultEnum.PARAM_INVALID); }
        var actor=links.actor(); var account=InternalAuthContextHolder.get();
        PaymentLinkDTOs.Query source=input==null?new PaymentLinkDTOs.Query():input;
        PaymentLinkQuery query=new PaymentLinkQuery();
        query.setMerchantId(source.getMerchantId());
        if (source.getKeyword() != null && !source.getKeyword().isBlank()) {
            throw new com.scott.payment.component.core.exception.ServiceException(com.scott.payment.component.core.enums.ApiResultEnum.PARAM_INVALID);
        }
        query.setLinkId(source.getLinkId()); query.setMerchantOrderNo(source.getMerchantOrderNo());
        query.setStatus(source.getStatus());
        query.setCurrency(source.getCurrency());
        query.setChannelCode(source.getChannelCode());
        query.setCreateTimeFrom(source.getCreateTimeFrom());
        query.setCreateTimeTo(source.getCreateTimeTo());
        CreateRequest command=new CreateRequest(); command.setRequestId(requestId); command.setTaskType("MERCHANT_PAYMENT_LINK");
        command.setCreatorAccountId(account.getAccountId()); command.setCreatorAccountName(account.getLoginAccount());
        command.setTaskName(LocaleContextHolder.getLocale().getLanguage().equals("zh")?"支付链接":"Payment Links");
        command.setLocale(LocaleContextHolder.getLocale().toLanguageTag()); command.setTimezone(frozenTimezone);
        command.setMerchantId(actor.getMerchantIds().iterator().next()); query.setMerchantId(command.getMerchantId());
        command.setPaymentLinkQuery(query); return data.submit(command);
    }
}

package com.scott.payment.admin.application.paymentlink;

import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkExportDTOs.*;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/** 仅供已签名 Data 导出任务读取，不从浏览器接受冻结的操作者范围。 */
@Service
public class AdminPaymentLinkExportSourceApplicationService {
    private final AdminPaymentLinkQueryService queries;
    public AdminPaymentLinkExportSourceApplicationService(AdminPaymentLinkQueryService queries) { this.queries=queries; }
    /** 按导出任务携带的操作者与分页参数读取链接，不绕过数据范围校验。 */
    public PageResult<Row> page(PageRequest request) {
        if(request.getCreatorAccountId()==null || request.getPageNo()<1 || request.getPageSize()<1 || request.getPageSize()>100) { throw new ServiceException(ApiResultEnum.PARAM_INVALID); }
        Query query=request.getQuery()==null?new Query():request.getQuery(); query.setPageNo(request.getPageNo()); query.setPageSize(request.getPageSize());
        Actor actor=new Actor(); actor.setAppCode("ADMIN"); actor.setActorId(String.valueOf(request.getCreatorAccountId()));
        actor.setAllMerchants(request.isAllMerchants()); actor.setMerchantIds(request.getMerchantIds());
        PageResult<View> page=queries.search(actor,query);
        return PageResult.of(page.getTotal(),page.getPageNo(),page.getPageSize(),page.getRecords().stream().map(this::row).toList());
    }
    private Row row(View source) { Row row=new Row();
        row.setLinkId(source.getLinkId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setLinkName(source.getLinkName());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setStatus(source.getStatus());
        row.setPaymentStatus(source.getPaymentStatus());
        row.setExpireTime(source.getExpireTime());
        row.setCreateTime(source.getCreateTime());
        row.setPaidTime(source.getPaidTime());
        row.setCreatedAppCode(source.getCreatedAppCode());
        row.setCreatedBy(source.getCreatedBy());
        return row;
    }
}

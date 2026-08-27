package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.temporal.ChronoUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementApplicationService
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算管理用例编排；列表和详情调用本地只读查询，取消与冲正绑定可信操作人后调用结算服务。
 * @status : create
 */
@Service
public class AdminSettlementApplicationService {

    private final SettlementInternalClient client;
    private final AdminSettlementQueryService queryService;

    public AdminSettlementApplicationService(SettlementInternalClient client,
                                             AdminSettlementQueryService queryService) {
        this.client = client;
        this.queryService = queryService;
    }

    /** @return 按业务日期和主键稳定倒序的结算批次标准分页 */
    public PageResult<BatchSummary> search(BatchSearchRequest request) {
        if (request == null || request.getBeginBusinessDate() == null
                || request.getEndBusinessDate() == null
                || request.getBeginBusinessDate().isAfter(request.getEndBusinessDate())
                || ChronoUnit.DAYS.between(request.getBeginBusinessDate(),
                request.getEndBusinessDate()) > 92) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return queryService.search(request);
    }

    /** @return 批次运营详情 */
    public BatchDetailResponse detail(String settlementBatchNo) {
        requireBatchNo(settlementBatchNo);
        return queryService.detail(settlementBatchNo.trim());
    }

    /** @return 入账前取消结果 */
    public BatchCommandResponse cancel(String settlementBatchNo, BatchCommandRequest request) {
        requireBatchNo(settlementBatchNo);
        return client.cancel(settlementBatchNo.trim(), command(request));
    }

    /** @return 已入账批次的独立冲正结果 */
    public BatchCommandResponse reverse(String settlementBatchNo, BatchCommandRequest request) {
        requireBatchNo(settlementBatchNo);
        return client.reverse(settlementBatchNo.trim(), command(request));
    }

    private InternalBatchCommandRequest command(BatchCommandRequest request) {
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || !validText(request.getRequestKey(), 64) || !validText(request.getReason(), 400)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalBatchCommandRequest command = new InternalBatchCommandRequest();
        command.setRequestKey(request.getRequestKey().trim());
        command.setExpectedVersion(request.getExpectedVersion());
        command.setReason(request.getReason().trim());
        command.setOperator(currentOperator());
        return command;
    }

    private void requireBatchNo(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("SB\\d{8}-\\d{8}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        String name = StringUtils.hasText(account.getRealName())
                ? account.getRealName() : account.getLoginAccount();
        return "admin-account:" + account.getAccountId() + "/" + name;
    }
}

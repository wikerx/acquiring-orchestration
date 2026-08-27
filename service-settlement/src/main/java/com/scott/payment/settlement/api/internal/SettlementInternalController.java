package com.scott.payment.settlement.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchDetailResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchResponse;
import com.scott.payment.settlement.application.SettlementBatchCommandApplicationService;
import com.scott.payment.settlement.service.SettlementManagementQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalController
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 仅供 service-admin 通过 HMAC 调用的结算批次查询、入账前取消和入账后冲正接口。
 * @status : create
 */
@RestController
@RequestMapping("/internal/settlement/v1/batches")
public class SettlementInternalController {

    private final SettlementManagementQueryService queryService;
    private final SettlementBatchCommandApplicationService commandService;

    public SettlementInternalController(SettlementManagementQueryService queryService,
                                        SettlementBatchCommandApplicationService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** @param request 有界日期和主键游标条件 @return 批次列表 */
    @PostMapping("/search")
    public CommonResult<BatchSearchResponse> search(@RequestBody BatchSearchRequest request) {
        return success(queryService.search(request));
    }

    /** @param settlementBatchNo 全局批次号 @return 汇率、汇总、净入账和异步联动详情 */
    @GetMapping("/{settlementBatchNo}")
    public CommonResult<BatchDetailResponse> detail(
            @PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(queryService.detail(settlementBatchNo));
    }

    /** 入账前取消并释放候选；期望版本防止页面基于过期状态操作。 */
    @PostMapping("/{settlementBatchNo}/cancel")
    public CommonResult<BatchCommandResponse> cancel(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request) {
        validateCommand(request);
        int released = commandService.cancelBeforePosting(
                settlementBatchNo, request.getExpectedVersion(), LocalDateTime.now());
        BatchCommandResponse response = new BatchCommandResponse();
        response.setSettlementBatchNo(settlementBatchNo);
        response.setResultBatchNo(settlementBatchNo);
        response.setResultStatus("CANCELLED");
        response.setReleasedCandidateCount(released);
        return success(response);
    }

    /** 已入账批次只允许创建独立冲正批，不直接覆盖原资金事实。 */
    @PostMapping("/{settlementBatchNo}/reverse")
    public CommonResult<BatchCommandResponse> reverse(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request) {
        validateCommand(request);
        String reversalBatchNo = commandService.reversePostedBatch(
                settlementBatchNo, request.getRequestKey(), request.getExpectedVersion(), LocalDateTime.now());
        BatchCommandResponse response = new BatchCommandResponse();
        response.setSettlementBatchNo(settlementBatchNo);
        response.setResultBatchNo(reversalBatchNo);
        response.setResultStatus("REVERSED");
        response.setReleasedCandidateCount(0);
        return success(response);
    }

    /** 校验审计命令字段；操作人和原因不参与资金计算，但必须进入 Admin 操作日志。 */
    private void validateCommand(BatchCommandRequest request) {
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getRequestKey() == null || request.getRequestKey().isBlank()
                || request.getRequestKey().trim().length() > 64
                || request.getReason() == null || request.getReason().isBlank()
                || request.getReason().trim().length() > 400
                || request.getOperator() == null || request.getOperator().isBlank()
                || request.getOperator().trim().length() > 128) {
            throw new IllegalArgumentException("settlement management command is invalid");
        }
    }
}

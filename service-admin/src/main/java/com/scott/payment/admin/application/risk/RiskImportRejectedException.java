package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import java.util.List;

/**
 * 保持导入事务回滚，同时向原请求返回有限、脱敏的行级失败摘要。
 */
public final class RiskImportRejectedException extends ServiceException {
    private static final long serialVersionUID = 1L;
    private final RiskDTOs.ImportResultResponse result;

    /**
     * 构造整批回滚结果；完整行级错误仍由独立导入日志事务保留。
     */
    public RiskImportRejectedException(String batchNo, int totalCount,
                                       List<AdminRiskImportLogService.ImportRowError> errors,
                                       String summary) {
        super(ApiResultEnum.PARAM_INVALID.getCode(), summary);
        result = new RiskDTOs.ImportResultResponse();
        result.setBatchNo(batchNo);
        result.setTotalCount(totalCount);
        result.setSuccessCount(0);
        result.setFailureCount(errors.size());
        result.setRolledBack(true);
        result.setErrors(errors.stream().limit(5)
                .map(error -> "line " + error.rowNo() + ": " + error.errorMessage()).toList());
    }

    /**
     * 返回受限摘要，不包含原始导入行。
     */
    public RiskDTOs.ImportResultResponse getResult() {
        return result;
    }
}

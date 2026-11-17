package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.RiskImportRejectedException;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.model.CommonResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 在导入应用事务已经回滚后保留原错误码，并附带结构化失败摘要。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AdminRiskCommonController.class)
public class AdminRiskImportExceptionHandler {
    /**
     * 只处理普通风控导入的整批回滚异常，不改变其他业务异常语义。
     */
    @ExceptionHandler(RiskImportRejectedException.class)
    public CommonResult<RiskDTOs.ImportResultResponse> rejected(RiskImportRejectedException exception) {
        CommonResult<RiskDTOs.ImportResultResponse> result = CommonResult.error(exception.getCode(), exception.getMessage());
        result.setData(exception.getResult());
        return result;
    }
}

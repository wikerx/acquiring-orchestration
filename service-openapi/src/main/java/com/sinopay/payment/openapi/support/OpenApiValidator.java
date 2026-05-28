package com.sinopay.payment.openapi.support;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiValidator
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口 DTO 属性校验器
 * @status : create
 */
@Component
public class OpenApiValidator {

    private final Validator validator;

    public OpenApiValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object target, Class<?>... validationGroups) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target, validationGroups);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<Object> violation = violations.iterator().next();
        throw new BizException(ErrorCode.PARAM_INVALID, violation.getPropertyPath() + " " + violation.getMessage());
    }
}

package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
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

    /**
     * Spring Bean Validation 校验器，用于执行 DTO 字段约束和自定义断言规则。
     */
    private final Validator validator;

    public OpenApiValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * 按指定分组校验开放 API DTO。
     *
     * @param target           待校验对象
     * @param validationGroups 校验分组
     */
    public void validate(Object target, Class<?>... validationGroups) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target, validationGroups);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<Object> violation = violations.iterator().next();
        throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_INVALID, violation.getPropertyPath() + " " + violation.getMessage());
    }
}

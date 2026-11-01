package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;


/**
 * 按接口注解声明的 Bean Validation 分组校验解密 DTO，并生成稳定字段级错误。
 */
@Component
public class OpenApiValidator {

    /**
     * Spring Bean Validation 校验器，用于执行 DTO 字段约束和自定义断言规则。
     */
    private final Validator validator;

    /**
     * 创建开放接口 DTO 校验器。
     *
     * @param validator Bean Validation 校验器
     */
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
        throw OpenApiContextualException.parameter(
                OpenApiResultCode.PARAM_INVALID,
                violation.getPropertyPath().toString(),
                violation.getMessage());
    }
}

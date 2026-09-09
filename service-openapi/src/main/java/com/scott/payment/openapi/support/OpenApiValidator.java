package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiValidator
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Validator 校验组件，位于 商户开放接口服务，执行参数、状态、权限或配置规则校验，失败时返回统一异常。
 * @status : create
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
        throw new ApiException(ApiResultEnum.PARAM_INVALID, violation.getMessage());
    }
}

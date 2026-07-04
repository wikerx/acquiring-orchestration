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
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口 DTO 属性校验器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiValidator
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Validator，位于 service-openapi 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 校验商户 OpenAPI业务规则，发现不符合要求的数据时抛出业务异常。
     * @param target 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param validationGroups 请求参数或业务处理上下文，不能为空时由上层校验约束。
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

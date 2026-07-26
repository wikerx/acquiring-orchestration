package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;


@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiValidator
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiValidator Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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

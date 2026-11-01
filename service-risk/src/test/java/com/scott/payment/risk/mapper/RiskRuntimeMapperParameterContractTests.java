package com.scott.payment.risk.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证风控 Mapper 注解 SQL 参数均显式声明 MyBatis 参数名。
 */
class RiskRuntimeMapperParameterContractTests {

    private static final Pattern SQL_PARAMETER = Pattern.compile("[#$]\\{\\s*([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 验证每个注解 SQL 方法参数都使用 Param 注解固定绑定名称。
     */
    @Test
    void shouldDeclareEveryAnnotatedSqlParameterWithMyBatisParam() {
        for (Method method : RiskRuntimeMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            Set<String> declaredParameters = Arrays.stream(method.getParameters())
                    .map(parameter -> parameter.getAnnotation(Param.class))
                    .filter(param -> param != null)
                    .map(Param::value)
                    .collect(Collectors.toSet());
            if (declaredParameters.isEmpty()) {
                continue;
            }
            Matcher matcher = SQL_PARAMETER.matcher(String.join("\n", select.value()));
            while (matcher.find()) {
                assertThat(declaredParameters)
                        .as("MyBatis parameter '%s' used by %s", matcher.group(1), method.getName())
                        .contains(matcher.group(1));
            }
        }
    }
}

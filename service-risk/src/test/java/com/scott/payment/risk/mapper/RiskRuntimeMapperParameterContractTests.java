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

class RiskRuntimeMapperParameterContractTests {

    private static final Pattern SQL_PARAMETER = Pattern.compile("[#$]\\{\\s*([A-Za-z_][A-Za-z0-9_]*)");

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

package com.scott.payment.openapi.api.rest;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRestControllerContractTest
 * @date : 2026-10-26 00:00
 * @email : scott_x@163.com
 * @description : 商户 /api/rest 开放接口的统一安全与响应契约测试，防止新增入口绕过验签解密或混用内部响应模型。
 * @status : create
 */
class OpenApiRestControllerContractTest {

    private static final String REST_CONTROLLER_PACKAGE = "com.scott.payment.openapi.api.rest";

    /**
     * 当前 15 个商户开放控制器必须只暴露 POST，并统一经过安全切面后返回 CommonResult。
     */
    @Test
    void shouldProtectEveryMerchantRestEndpointWithUnifiedContract() {
        List<Class<?>> controllerTypes = merchantRestControllerTypes();

        assertThat(controllerTypes).hasSize(15);
        assertThat(controllerTypes).allSatisfy(controllerType -> {
            List<Method> endpointMethods = Arrays.stream(controllerType.getDeclaredMethods())
                    .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                    .toList();

            assertThat(endpointMethods).as(controllerType.getName()).isNotEmpty();
            assertThat(endpointMethods).allSatisfy(method -> {
                assertThat(method.getAnnotation(PostMapping.class))
                        .as(controllerType.getSimpleName() + "#" + method.getName() + " must use POST")
                        .isNotNull();
                assertThat(method.getAnnotation(VerificationAndProcessing.class))
                        .as(controllerType.getSimpleName() + "#" + method.getName() + " must use merchant security processing")
                        .isNotNull();
                assertThat(method.getReturnType())
                        .as(controllerType.getSimpleName() + "#" + method.getName() + " must use CommonResult")
                        .isEqualTo(CommonResult.class);
            });
        });
    }

    /**
     * 从测试类路径发现真实 REST 控制器，并只选择类级路径属于 /api/rest 的商户开放接口。
     */
    private List<Class<?>> merchantRestControllerTypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        return scanner.findCandidateComponents(REST_CONTROLLER_PACKAGE).stream()
                .<Class<?>>map(beanDefinition -> loadClass(beanDefinition.getBeanClassName()))
                .filter(this::hasMerchantRestBasePath)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /** 类级基础路径必须明确以 /api/rest 开头，浏览器、回调、健康检查和内部接口不属于此契约。 */
    private boolean hasMerchantRestBasePath(Class<?> controllerType) {
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        return requestMapping != null
                && Arrays.stream(requestMapping.value()).anyMatch(path -> path.startsWith("/api/rest"));
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to load OpenAPI controller: " + className, exception);
        }
    }
}

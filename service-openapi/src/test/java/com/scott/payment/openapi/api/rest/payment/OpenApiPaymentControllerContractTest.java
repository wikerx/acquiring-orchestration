package com.scott.payment.openapi.api.rest.payment;

import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiAuthorizationController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiCaptureController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiIncrementalAuthorizationController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPaymentController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPaymentQueryController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPreAuthCompletionController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPreAuthorizationController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiRefundController;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiVoidController;
import com.scott.payment.openapi.application.payment.OpenApiAuthorizationApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiCaptureApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiIncrementalAuthorizationApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiPaymentApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiPaymentQueryApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiPreAuthCompletionApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiPreAuthorizationApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiRefundApplicationService;
import com.scott.payment.openapi.application.payment.OpenApiVoidApplicationService;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentControllerContractTest
 * @date : 2026-07-14 17:05
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单交易控制器契约测试，校验一个外部接口一个控制器、一个应用服务以及安全注解、版本和路径不被误改。
 * @status : create
 */
@Slf4j
class OpenApiPaymentControllerContractTest {

    /**
     * 校验所有收单交易开放接口都由独立控制器承载，并保留统一验签、解密、防重放注解。
     */
    @Test
    void shouldKeepOneControllerPerOpenApiPaymentEndpoint() throws NoSuchMethodException {
        log.info("开始校验收单 OpenAPI 控制器契约，caseCount: {}", controllerCases().size());
        for (ControllerCase controllerCase : controllerCases()) {
            Method method = controllerCase.controllerType().getDeclaredMethod(
                    controllerCase.methodName(),
                    HttpServletRequest.class,
                    String.class,
                    ApiMerchantPaymentRequestDTO.class
            );

            assertControllerAnnotation(controllerCase);
            assertPostMapping(method, controllerCase);
            assertVerificationAndProcessing(method, controllerCase);
            assertOnlyOneEndpointMethod(controllerCase.controllerType());
            assertDedicatedApplicationService(controllerCase);
        }
        log.info("收单 OpenAPI 控制器契约校验完成，caseCount: {}", controllerCases().size());
    }

    /**
     * 校验控制器类级版本和统一基础路由。
     *
     * @param controllerCase 控制器契约用例
     */
    private void assertControllerAnnotation(ControllerCase controllerCase) {
        ApiVersion apiVersion = controllerCase.controllerType().getAnnotation(ApiVersion.class);
        RequestMapping requestMapping = controllerCase.controllerType().getAnnotation(RequestMapping.class);

        assertThat(apiVersion).isNotNull();
        assertThat(apiVersion.apiVersion()).isEqualTo(controllerCase.apiVersion());
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/rest/payment/{version}");
    }

    /**
     * 校验每个控制器方法只暴露对应的 POST 路径。
     *
     * @param method 控制器业务方法
     * @param controllerCase 控制器契约用例
     */
    private void assertPostMapping(Method method, ControllerCase controllerCase) {
        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertThat(postMapping).isNotNull();
        assertThat(postMapping.value()).containsExactly(controllerCase.path());
    }

    /**
     * 校验开放接口安全链路注解和 Bean Validation 分组。
     *
     * @param method 控制器业务方法
     * @param controllerCase 控制器契约用例
     */
    private void assertVerificationAndProcessing(Method method, ControllerCase controllerCase) {
        VerificationAndProcessing annotation = method.getAnnotation(VerificationAndProcessing.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.dataReceiver()).isEqualTo(ApiMerchantPaymentRequestDTO.class);
        assertThat(annotation.validationGroups()).containsExactly(controllerCase.validationGroups());
    }

    /**
     * 校验一个控制器只承载一个外部业务入口，避免再次把多个交易动作合并到同一个类。
     *
     * @param controllerType 控制器类型
     */
    private void assertOnlyOneEndpointMethod(Class<?> controllerType) {
        long endpointMethodCount = List.of(controllerType.getDeclaredMethods()).stream()
                .filter(method -> method.getAnnotation(PostMapping.class) != null)
                .count();

        assertThat(endpointMethodCount).isEqualTo(1);
    }

    /**
     * 校验每个控制器只注入对应的独立应用服务，避免重新汇聚成单个 OpenApiPaymentApplicationService。
     *
     * @param controllerCase 控制器契约用例
     */
    private void assertDedicatedApplicationService(ControllerCase controllerCase) {
        Constructor<?>[] constructors = controllerCase.controllerType().getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(controllerCase.applicationServiceType());
    }

    /**
     * 收单交易开放接口控制器契约列表。V2 支付入口属于早期版本测试代码，当前正式对外仅保留 V1。
     *
     * @return 控制器契约用例集合
     */
    private List<ControllerCase> controllerCases() {
        return List.of(
                new ControllerCase(OpenApiPaymentController.class, OpenApiPaymentApplicationService.class, 1, "createPayment", "/payment",
                        ApiMerchantPaymentRequestDTO.Payment.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiAuthorizationController.class, OpenApiAuthorizationApplicationService.class, 1, "createAuthorization", "/authorization",
                        ApiMerchantPaymentRequestDTO.Authorization.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiPreAuthorizationController.class, OpenApiPreAuthorizationApplicationService.class, 1, "createPreAuthorization", "/pre-authorization",
                        ApiMerchantPaymentRequestDTO.PreAuthorization.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiIncrementalAuthorizationController.class, OpenApiIncrementalAuthorizationApplicationService.class, 1, "createIncrementalAuthorization", "/incremental-authorization",
                        ApiMerchantPaymentRequestDTO.IncrementalAuthorization.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiCaptureController.class, OpenApiCaptureApplicationService.class, 1, "capture", "/capture",
                        ApiMerchantPaymentRequestDTO.Capture.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiPreAuthCompletionController.class, OpenApiPreAuthCompletionApplicationService.class, 1, "preAuthCompletion", "/pre-auth-completion",
                        ApiMerchantPaymentRequestDTO.PreAuthCompletion.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiRefundController.class, OpenApiRefundApplicationService.class, 1, "refund", "/refund",
                        ApiMerchantPaymentRequestDTO.Refund.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiVoidController.class, OpenApiVoidApplicationService.class, 1, "voidPayment", "/void",
                        ApiMerchantPaymentRequestDTO.AuthorizationCancel.class, ApiMerchantPaymentRequestDTO.Format.class),
                new ControllerCase(OpenApiPaymentQueryController.class, OpenApiPaymentQueryApplicationService.class, 1, "query", "/query",
                        ApiMerchantPaymentRequestDTO.Query.class, ApiMerchantPaymentRequestDTO.Format.class)
        );
    }

    /**
     * 控制器契约用例，集中描述开放接口的控制器类型、版本、路径和校验分组。
     *
     * @param controllerType 控制器类型
     * @param applicationServiceType 控制器专属应用服务类型
     * @param apiVersion API 版本号
     * @param methodName 控制器方法名
     * @param path POST 子路径
     * @param validationGroups Bean Validation 分组
     */
    private record ControllerCase(Class<?> controllerType,
                                  Class<?> applicationServiceType,
                                  int apiVersion,
                                  String methodName,
                                  String path,
                                  Class<?>... validationGroups) {
    }
}

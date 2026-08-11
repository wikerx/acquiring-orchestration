package com.scott.payment.openapi.api.rest.reference;

import com.scott.payment.component.web.version.ApiVersion;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.api.rest.reference.v1.OpenApiCardBinLookupController;
import com.scott.payment.openapi.api.rest.reference.v1.OpenApiIpLookupController;
import com.scott.payment.openapi.application.reference.OpenApiReferenceDataApplicationService;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataControllerContractTest
 * @date : 2026-08-11 15:43
 * @email : scott_x@163.com
 * @description : 商户基础数据检索控制器契约测试，保护 V1 路径、资源拆分及统一验签解密注解
 * @status : create
 */
@Slf4j
class OpenApiReferenceDataControllerContractTest {

    /**
     * 校验 IP 和卡 BIN 各自使用独立控制器并完整接入商户 OpenAPI 安全链路。
     */
    @Test
    void shouldProtectReferenceDataEndpointsWithOpenApiSecurityPipeline() throws NoSuchMethodException {
        log.info("开始校验基础数据 OpenAPI 控制器契约，接口数量: 2");

        assertController(OpenApiIpLookupController.class, "queryIp",
                "/api/rest/ip/{version}", "/query", IpLookupRequestDTO.class);
        assertController(OpenApiCardBinLookupController.class, "queryCardBin",
                "/api/rest/card-bin/{version}", "/query", CardBinLookupRequestDTO.class);

        log.info("基础数据 OpenAPI 控制器契约校验完成，接口数量: 2");
    }

    private void assertController(Class<?> controllerType,
                                  String methodName,
                                  String basePath,
                                  String path,
                                  Class<?> requestType) throws NoSuchMethodException {
        ApiVersion apiVersion = controllerType.getAnnotation(ApiVersion.class);
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        Method method = controllerType.getDeclaredMethod(
                methodName, HttpServletRequest.class, String.class, requestType);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        VerificationAndProcessing verification = method.getAnnotation(VerificationAndProcessing.class);
        Constructor<?>[] constructors = controllerType.getDeclaredConstructors();

        assertThat(apiVersion).isNotNull();
        assertThat(apiVersion.apiVersion()).isEqualTo(1);
        assertThat(requestMapping.value()).containsExactly(basePath);
        assertThat(postMapping.value()).containsExactly(path);
        assertThat(verification).isNotNull();
        assertThat(verification.dataReceiver()).isEqualTo(requestType);
        assertThat(verification.requiredHeader()).isTrue();
        assertThat(verification.validator()).isTrue();
        assertThat(verification.deferIpWhitelistToRisk()).isFalse();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(OpenApiReferenceDataApplicationService.class);
    }
}

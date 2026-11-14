package com.scott.payment.merchant.api.system;

import com.scott.payment.component.web.template.ManagedFileTemplateEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 商户端文件模板下载只能经过模板中心只读入口。 */
class MerchantFileTemplateEndpointGovernanceTest {

    @Test
    void shouldRejectUnmanagedTemplateDownloadEndpoints() throws Exception {
        List<String> violations = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("com.scott.payment.merchant.api")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                boolean fileResponse = method.getReturnType() == Void.TYPE
                        || method.getReturnType() == byte[].class
                        || Resource.class.isAssignableFrom(method.getReturnType())
                        || StreamingResponseBody.class.isAssignableFrom(method.getReturnType())
                        || ResponseEntity.class.isAssignableFrom(method.getReturnType());
                if (mapping == null || !fileResponse) {
                    continue;
                }
                java.util.stream.Stream<String> paths = java.util.stream.Stream.concat(
                        java.util.Arrays.stream(mapping.value()), java.util.Arrays.stream(mapping.path()));
                boolean templateDownload = paths
                        .map(path -> path.toLowerCase(Locale.ROOT))
                        .anyMatch(path -> path.contains("template"));
                if (templateDownload && method.getAnnotation(ManagedFileTemplateEndpoint.class) == null) {
                    violations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}

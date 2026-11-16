package com.scott.payment.admin.api.system;

import com.scott.payment.component.web.template.LegacyFileTemplateAdapter;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理端新增文件模板下载接口必须进入模板中心治理。 */
class FileTemplateEndpointGovernanceTest {

    @Test
    void shouldRejectUnmanagedOrExpiredTemplateDownloadEndpoints() throws Exception {
        List<String> violations = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("com.scott.payment.admin.api")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                if (!isFileTemplateDownload(method)) {
                    continue;
                }
                ManagedFileTemplateEndpoint managed = method.getAnnotation(ManagedFileTemplateEndpoint.class);
                LegacyFileTemplateAdapter legacy = method.getAnnotation(LegacyFileTemplateAdapter.class);
                if (managed == null && legacy == null) {
                    violations.add(controller.getSimpleName() + "#" + method.getName() + " is not governed");
                    continue;
                }
                if (managed != null && managed.value().isBlank()) {
                    violations.add(controller.getSimpleName() + "#" + method.getName() + " has blank template code");
                }
                if (legacy != null) {
                    LocalDate deadline = LocalDate.parse(legacy.removeAfter());
                    if (deadline.isBefore(LocalDate.now())) {
                        violations.add(controller.getSimpleName() + "#" + method.getName()
                                + " legacy adapter expired on " + deadline);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private boolean isFileTemplateDownload(Method method) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        if (mapping == null) {
            return false;
        }
        boolean fileResponse = method.getReturnType() == Void.TYPE
                || method.getReturnType() == byte[].class
                || Resource.class.isAssignableFrom(method.getReturnType())
                || StreamingResponseBody.class.isAssignableFrom(method.getReturnType())
                || ResponseEntity.class.isAssignableFrom(method.getReturnType());
        if (!fileResponse) {
            return false;
        }
        List<String> paths = new ArrayList<>();
        paths.addAll(List.of(mapping.value()));
        paths.addAll(List.of(mapping.path()));
        for (String path : paths) {
            String normalized = path.toLowerCase(Locale.ROOT);
            if (normalized.contains("template")) {
                return true;
            }
        }
        return false;
    }
}

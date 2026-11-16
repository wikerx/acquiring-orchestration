package com.scott.payment.component.web.upload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class UploadSecurityConfigurationTest {
    @Test void defaultContextStartsWithoutScannerConnection() {
        new ApplicationContextRunner().withUserConfiguration(UploadSecurityConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MalwareScanner.class).hasSingleBean(ManagedImageStore.class);
        });
    }
    @Test void disablingScanStillStartsButRefusesUploads() {
        new ApplicationContextRunner().withUserConfiguration(UploadSecurityConfiguration.class).withPropertyValues("acquiring.upload-security.enabled=false").run(context -> {
            assertThat(context).hasNotFailed(); assertThatThrownBy(() -> context.getBean(MalwareScanner.class).scan(new byte[]{1})).hasMessageContaining("安全检查暂不可用");
        });
    }
}

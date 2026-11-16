package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.auth.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

/** 仅显式启用时连接本机真实 clamd，不把替身测试作为引擎部署证据。 */
@EnabledIfSystemProperty(named = "clamav.integration", matches = "true")
class ClamAvContainerIntegrationTest {
    @Test void realEngineAcceptsNormalizedPngAndRejectsEicar() throws Exception {
        var properties = new UploadSecurityProperties(); properties.setTimeoutMillis(10000);
        var scanner = new ClamAvMalwareScanner(properties);
        var account = new InternalAuthAccount(); account.setAppCode("ADMIN"); account.setUserId(1L); account.setAccountId(1L);
        InternalAuthContextHolder.set(account);
        try {
            var original = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(24, 12, BufferedImage.TYPE_INT_ARGB), "png", original);
            var processor = new SafeImageProcessor(new UploadSecurityService(scanner, properties), scanner);
            assertThat(processor.process(new MockMultipartFile("file", "safe.png", "image/png", original.toByteArray()), true)).isNotEmpty();
            String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
            assertThatThrownBy(() -> scanner.scan(eicar.getBytes(StandardCharsets.US_ASCII))).hasMessageContaining("文件未通过安全检查");
        } finally { scanner.shutdown(); InternalAuthContextHolder.clear(); }
    }
}

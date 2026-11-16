package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadSecurityServiceTest {
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final UploadSecurityProperties properties = new UploadSecurityProperties();
    private final UploadSecurityService uploads = new UploadSecurityService(scanner, properties);
    @BeforeEach void login() {
        InternalAuthAccount account = new InternalAuthAccount(); account.setAppCode("ADMIN"); account.setUserId(1L); account.setAccountId(2L);
        InternalAuthContextHolder.set(account);
    }
    @AfterEach void logout() { InternalAuthContextHolder.clear(); }
    private MockMultipartFile csv(String name, String type, String data) { return new MockMultipartFile("file", name, type, data.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    @Test void unauthorizedRequestsDoNotReadOrScanFiles() throws Exception {
        InternalAuthContextHolder.clear(); var file = mock(org.springframework.web.multipart.MultipartFile.class);
        assertThatThrownBy(() -> uploads.validate(file, Set.of("csv"), 100)).hasMessageContaining("登录状态");
        verifyNoInteractions(file, scanner);
    }
    @Test void rejectsDisguisedImageBeforeScanning() {
        assertThatThrownBy(() -> uploads.validate(csv("a.png", "image/png", "<script>bad</script>"), Set.of("png"), 100)).hasMessageContaining("内容");
        verifyNoInteractions(scanner);
    }
    @Test void rejectsForgedMimeBeforeScanning() {
        assertThatThrownBy(() -> uploads.validate(csv("a.csv", "image/png", "a,b"), Set.of("csv"), 100)).hasMessageContaining("类型"); verifyNoInteractions(scanner);
    }
    @Test void rejectsOversizePathAndControlCharacters() {
        for (String name : Set.of("../a.csv", "a:bad.csv", "a\nc.csv")) assertThatThrownBy(() -> uploads.validate(csv(name, "text/csv", "a,b"), Set.of("csv"), 100)).hasMessageContaining("文件名");
        assertThatThrownBy(() -> uploads.validate(csv("a.csv", "text/csv", "a,b"), Set.of("csv"), 2)).hasMessageContaining("大小"); verifyNoInteractions(scanner);
    }
    @Test void rejectsBinaryCsvAndUnsupportedExtension() {
        assertThatThrownBy(() -> uploads.validate(csv("a.csv", "text/csv", "a\0b"), Set.of("csv"), 100)).hasMessageContaining("内容");
        assertThatThrownBy(() -> uploads.validate(csv("a.svg", "image/svg+xml", "a,b"), Set.of("png", "jpg"), 100)).hasMessageContaining("格式"); verifyNoInteractions(scanner);
    }
    @Test void returnsOnlyScannedImmutableBytes() throws Exception {
        var file = csv("a.csv", "text/csv", "a,b"); var checked = uploads.validate(file, Set.of("csv"), 100);
        verify(scanner).scan(eq(file.getBytes())); byte[] copy = checked.getBytes(); copy[0] = 0;
        assertThat(checked.getBytes()).isEqualTo(file.getBytes());
    }
    @Test void scannerFailurePreventsAcceptance() {
        doThrow(new IllegalStateException("unavailable")).when(scanner).scan(any());
        assertThatThrownBy(() -> uploads.validate(csv("a.csv", "text/csv", "a,b"), Set.of("csv"), 100)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rateLimitRejectsBeforeFileRead() {
        properties.setUploadsPerMinute(1); uploads.validate(csv("a.csv", "text/csv", "a,b"), Set.of("csv"), 100);
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        assertThatThrownBy(() -> uploads.validate(file, Set.of("csv"), 100)).hasMessageContaining("频繁"); verifyNoInteractions(file);
    }
    @Test void renamedWebpReportsActualFormatInsteadOfSize() {
        byte[] webp = new byte[]{82,73,70,70,20,0,0,0,87,69,66,80,86,80,56,32};
        var file = new MockMultipartFile("file", "Mastercard_2019_logo.png", "image/png", webp);
        assertThatThrownBy(() -> uploads.validate(file, Set.of("png", "jpg", "jpeg"), 2097152))
                .hasMessageContaining("实际格式为 WebP").hasMessageNotContaining("大小");
        verifyNoInteractions(scanner);
    }
}

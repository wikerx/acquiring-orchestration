package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeImageProcessorTest {
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final SafeImageProcessor images = new SafeImageProcessor(new UploadSecurityService(scanner, new UploadSecurityProperties()), scanner);
    @BeforeEach void login() { var a = new InternalAuthAccount(); a.setUserId(1L); a.setAccountId(2L); a.setAppCode("ADMIN"); InternalAuthContextHolder.set(a); }
    @AfterEach void logout() { InternalAuthContextHolder.clear(); }
    private byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output); return output.toByteArray();
    }
    @Test void avatarIsNormalizedAndBothOriginalAndOutputAreScanned() throws Exception {
        byte[] original = png(80, 40); byte[] output = images.process(new MockMultipartFile("file", "photo.png", "image/png", original), true);
        var image = ImageIO.read(new ByteArrayInputStream(output)); assertThat(image.getWidth()).isEqualTo(512); assertThat(image.getHeight()).isEqualTo(512);
        var order = inOrder(scanner); order.verify(scanner).scan(eq(original)); order.verify(scanner).scan(eq(output));
    }
    @Test void logoPreservesRatioAndTransparency() throws Exception {
        byte[] output = images.process(new MockMultipartFile("file", "logo.png", "image/png", png(1200, 300)), false);
        var image = ImageIO.read(new ByteArrayInputStream(output)); assertThat(image.getWidth()).isEqualTo(1024); assertThat(image.getHeight()).isEqualTo(256);
        assertThat(image.getColorModel().hasAlpha()).isTrue(); assertThat(image.getRGB(0, 0) >>> 24).isZero();
    }
    @Test void truncatedImageIsNotAcceptedAfterCleanScan() {
        byte[] header = {(byte)137,80,78,71,13,10,26,10};
        assertThatThrownBy(() -> images.process(new MockMultipartFile("file", "bad.png", "image/png", header), false)).hasMessageContaining("图片");
    }
    @Test void rejectsPixelLimitBeforeFullDecode() throws Exception {
        byte[] content = png(1, 1); java.nio.ByteBuffer.wrap(content, 16, 4).putInt(5000);
        assertThatThrownBy(() -> images.process(new MockMultipartFile("file", "big.png", "image/png", content), false)).hasMessageContaining("图片");
    }
    @Test void transformedImageMustAlsoPassScan() throws Exception {
        doNothing().doThrow(new IllegalStateException("scan failed")).when(scanner).scan(any());
        assertThatThrownBy(() -> images.process(new MockMultipartFile("file", "p.png", "image/png", png(10, 10)), true)).hasMessageContaining("scan failed");
    }
}

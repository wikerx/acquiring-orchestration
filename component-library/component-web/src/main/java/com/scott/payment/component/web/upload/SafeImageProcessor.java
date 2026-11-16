package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** 仅解码 PNG/JPEG，在分配像素内存前检查尺寸；重新编码不保留 EXIF 等原始元数据。 */
public class SafeImageProcessor {
    private final UploadSecurityService uploads;
    private final MalwareScanner scanner;
    private final Semaphore permits = new Semaphore(2);

    public SafeImageProcessor(UploadSecurityService uploads, MalwareScanner scanner) {
        this.uploads = uploads;
        this.scanner = scanner;
    }

    public byte[] process(MultipartFile file, boolean square) {
        if (!permits.tryAcquire()) throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(), "图片处理繁忙，请稍后重试");
        try {
            MultipartFile checked = uploads.validate(file, Set.of("png", "jpg", "jpeg"), 2L * 1024 * 1024);
            try (var input = new MemoryCacheImageInputStream(checked.getInputStream())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                ImageReader reader = readers.next();
                try {
                    if (!Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw invalid();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 16000000) throw invalid();
                    BufferedImage source = reader.read(0);
                    int edge = Math.min(width, height);
                    double scale = Math.min(1.0, 1024.0 / Math.max(width, height));
                    int outputWidth = square ? 512 : Math.max(1, (int) Math.round(width * scale));
                    int outputHeight = square ? 512 : Math.max(1, (int) Math.round(height * scale));
                    BufferedImage result = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = result.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        int left = square ? (width - edge) / 2 : 0;
                        int top = square ? (height - edge) / 2 : 0;
                        graphics.drawImage(source, 0, 0, outputWidth, outputHeight, left, top,
                                square ? left + edge : width, square ? top + edge : height, null);
                    } finally { graphics.dispose(); source.flush(); }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(result, "png", output)) throw invalid();
                    result.flush();
                    byte[] content = output.toByteArray();
                    if (content.length > 2 * 1024 * 1024) throw invalid();
                    scanner.scan(content);
                    return content;
                } finally { reader.dispose(); }
            } catch (IOException | IllegalArgumentException exception) { throw invalid(); }
        } finally { permits.release(); }
    }

    private static ServiceException invalid() {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "图片已损坏、尺寸过大或无法安全处理，请更换 PNG/JPEG 图片");
    }
}

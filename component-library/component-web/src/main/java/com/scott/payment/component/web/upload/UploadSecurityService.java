package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 业务先检查对象权限，再调用此组件；返回的不可变快照保证后续处理使用已扫描字节。 */
public class UploadSecurityService {
    private final MalwareScanner scanner;
    private final UploadSecurityProperties properties;
    private final Map<String, Window> rateWindows = new ConcurrentHashMap<>();

    public UploadSecurityService(MalwareScanner scanner, UploadSecurityProperties properties) {
        this.scanner = scanner;
        this.properties = properties;
    }

    /** 业务完成对象授权后调用；依次校验会话、频率、格式及大小并扫描内容，只返回已通过扫描的字节快照。 */
    public MultipartFile validate(MultipartFile file, Set<String> extensions, long maxBytes) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getUserId() == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        admit(account);
        long limit = Math.min(Math.min(maxBytes, properties.getMaxFileBytes()), 20L * 1024 * 1024);
        if (file == null || limit <= 0 || file.isEmpty() || file.getSize() <= 0 || file.getSize() > limit) {
            throw invalid("文件为空或超过此功能允许的大小限制");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.length() > 255 || filename.contains("/") || filename.contains("\\")
                || filename.contains(":") || filename.chars().anyMatch(Character::isISOControl)) {
            throw invalid("文件名不合法");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(extension)) throw invalid("此功能不支持该文件格式");
        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = input.readNBytes((int) limit + 1);
        } catch (IOException exception) { throw invalid("读取上传文件失败"); }
        if (content.length == 0 || content.length > limit || content.length != file.getSize()) {
            throw invalid("文件大小不合法或超过限制");
        }
        String contentType = detect(content, extension);
        String claimed = file.getContentType();
        if (claimed != null && !claimed.isBlank()) {
            claimed = claimed.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!claimed.equals(contentType) && !claimed.equals("application/octet-stream")
                    && !(extension.equals("csv") && Set.of("text/plain", "application/vnd.ms-excel").contains(claimed))
                    && !(extension.equals("zip") && claimed.equals("application/x-zip-compressed"))) {
                throw invalid("文件声明类型与实际内容不一致");
            }
        }
        scanner.scan(content);
        return new Snapshot(file.getName(), filename, contentType, content);
    }

    private void admit(InternalAuthAccount account) {
        long minute = System.currentTimeMillis() / 60000;
        String key = account.getAppCode() + ":" + account.getAccountId();
        if (rateWindows.size() > 10000) {
            rateWindows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
            if (rateWindows.size() > 10000 && !rateWindows.containsKey(key)) throw busy();
        }
        Window window = rateWindows.compute(key, (ignored, old) -> old == null || old.minute != minute
                ? new Window(minute, 1) : new Window(minute, old.count + 1));
        if (window.count > Math.max(1, properties.getUploadsPerMinute())) throw busy();
    }

    private String detect(byte[] content, String extension) {
        boolean matches = switch (extension) {
            case "png" -> starts(content, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "jpg", "jpeg" -> starts(content, new byte[]{(byte) 255, (byte) 216, (byte) 255});
            case "pdf" -> starts(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "xlsx", "docx" -> starts(content, new byte[]{80, 75, 3, 4});
            case "zip" -> starts(content, new byte[]{80, 75, 3, 4}) || starts(content, new byte[]{80, 75, 5, 6});
            case "xls" -> starts(content, new byte[]{(byte) 208, (byte) 207, 17, (byte) 224, (byte) 161, (byte) 177, 26, (byte) 225});
            case "csv" -> validText(content);
            default -> false;
        };
        if (!matches) {
            if (Set.of("png", "jpg", "jpeg").contains(extension)) {
                String actual = actualImageFormat(content);
                if (actual != null && !Set.of("PNG", "JPEG").contains(actual)) {
                    throw invalid("文件实际格式为 " + actual + "。仅支持 PNG/JPEG，请重新导出后上传。");
                }
                if (actual != null) throw invalid("文件实际格式为 " + actual + "，与文件扩展名不一致，请使用正确的图片文件。");
                throw invalid("文件内容无法识别为有效的 PNG/JPEG 图片，文件可能已损坏。");
            }
            throw invalid("文件内容与扩展名不一致，或文件已损坏");
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "zip" -> "application/zip";
            default -> "text/csv";
        };
    }

    private static String actualImageFormat(byte[] content) {
        if (starts(content, new byte[]{(byte) 137,80,78,71,13,10,26,10})) return "PNG";
        if (starts(content, new byte[]{(byte) 255,(byte) 216,(byte) 255})) return "JPEG";
        if (content.length >= 12 && starts(content, new byte[]{82,73,70,70})
                && content[8] == 87 && content[9] == 69 && content[10] == 66 && content[11] == 80) return "WebP";
        if (starts(content, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || starts(content, "GIF89a".getBytes(StandardCharsets.US_ASCII))) return "GIF";
        if (starts(content, new byte[]{66,77})) return "BMP";
        String prefix = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8).stripLeading();
        if (prefix.startsWith("<svg")) return "SVG";
        return null;
    }

    private boolean validText(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
            return text.chars().noneMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t');
        } catch (CharacterCodingException exception) { return false; }
    }

    private static boolean starts(byte[] content, byte[] signature) {
        return content.length >= signature.length && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    private static ServiceException invalid(String message) { return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message); }
    private static ServiceException busy() { return new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(), "上传操作过于频繁，请稍后重试"); }
    private record Window(long minute, int count) { }

    private record Snapshot(String name, String originalFilename, String contentType, byte[] content) implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File target) throws IOException { java.nio.file.Files.write(target.toPath(), content); }
        @Override public void transferTo(Path target) throws IOException { java.nio.file.Files.write(target, content); }
    }
}

package com.scott.payment.admin.support.template;

import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * 模板上传安全校验器。
 *
 * <p>不信任浏览器声明的 MIME 和文件名；使用文件签名、容器目录和 UTF-8 解码结果确认类型，
 * 拒绝宏、路径穿越、控制字符、超限文件和异常压缩包。</p>
 */
@Component
public class TemplateFileSecurityValidator {

    private static final long DEFAULT_MAX_SIZE = 20L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_SIZE = 32L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2048;

    private final ObjectStorageProperties properties;

    public TemplateFileSecurityValidator(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    /** 读取并确认上传文件与模板定义扩展名一致。 */
    public ValidatedFile validate(MultipartFile file, String expectedExtension) {
        String extension = normalizeExtension(expectedExtension);
        if (file == null || file.isEmpty()) {
            throw invalid("中文和英文模板文件均不能为空");
        }
        long maxSize = properties.getMaxFileSizeBytes() > 0
                ? properties.getMaxFileSizeBytes() : DEFAULT_MAX_SIZE;
        if (file.getSize() <= 0 || file.getSize() > maxSize) {
            throw invalid("模板文件大小必须大于0且不能超过" + maxSize + "字节");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "读取模板文件失败");
        }
        if (content.length == 0 || content.length > maxSize) {
            throw invalid("模板文件大小不合法");
        }
        String contentType = detectAndValidate(content, extension);
        String filename = safeFilename(file.getOriginalFilename(), extension);
        return new ValidatedFile(filename, extension, contentType, content, sha256(content));
    }

    /** 文件名只用于审计展示，不参与对象键生成。 */
    private String safeFilename(String originalFilename, String extension) {
        String filename = StringUtils.hasText(originalFilename)
                ? originalFilename.trim().replace('\\', '/') : "template." + extension;
        filename = filename.substring(filename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "");
        if (!filename.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            throw invalid("上传文件扩展名必须为 ." + extension);
        }
        if (filename.length() > 255 || filename.contains("..")) {
            throw invalid("模板文件名不合法");
        }
        return filename;
    }

    private String detectAndValidate(byte[] content, String extension) {
        return switch (extension) {
            case "pdf" -> {
                require(startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII)), "PDF文件签名不正确");
                yield "application/pdf";
            }
            case "csv" -> {
                validateUtf8Text(content);
                yield "text/csv;charset=UTF-8";
            }
            case "xlsx" -> {
                Set<String> entries = inspectZip(content);
                require(entries.contains("[Content_Types].xml") && entries.stream().anyMatch(name -> name.startsWith("xl/")),
                        "XLSX文件结构不正确");
                require(entries.stream().noneMatch(name -> name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")),
                        "模板文件禁止包含宏");
                yield "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            case "docx" -> {
                Set<String> entries = inspectZip(content);
                require(entries.contains("[Content_Types].xml") && entries.stream().anyMatch(name -> name.startsWith("word/")),
                        "DOCX文件结构不正确");
                require(entries.stream().noneMatch(name -> name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")),
                        "模板文件禁止包含宏");
                yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            case "zip" -> {
                inspectZip(content);
                yield "application/zip";
            }
            default -> throw invalid("不支持的模板扩展名");
        };
    }

    private Set<String> inspectZip(byte[] content) {
        require(content.length >= 4 && content[0] == 0x50 && content[1] == 0x4B, "压缩文件签名不正确");
        Set<String> entries = new HashSet<>();
        long total = 0;
        int count = 0;
        // Office/POI 会生成带 Data Descriptor 的 STORED 条目。JDK ZipInputStream 会把这种合法
        // XLSX 误判为 "invalid entry size"；Commons Compress 显式开启该格式支持后再执行相同
        // 的条目数、路径和解压体积限制。
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                count++;
                require(count <= MAX_ZIP_ENTRIES, "压缩文件条目过多");
                String name = entry.getName().replace('\\', '/');
                require(!name.startsWith("/") && !name.contains("../") && !name.contains("\u0000"), "压缩文件包含非法路径");
                require(input.canReadEntryData(entry), "压缩文件包含不支持的条目");
                entries.add(name);
                if (!entry.isDirectory()) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        require(total <= MAX_UNCOMPRESSED_SIZE, "压缩文件解压后体积过大");
                    }
                }
            }
        } catch (IOException exception) {
            throw invalid("压缩文件无法读取");
        }
        require(!entries.isEmpty(), "压缩文件不能为空");
        return entries;
    }

    private void validateUtf8Text(byte[] content) {
        for (byte value : content) {
            require(value != 0, "CSV模板不能包含二进制空字节");
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw invalid("CSV模板必须使用UTF-8编码");
        }
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            throw invalid("模板扩展名不能为空");
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("xlsx", "csv", "pdf", "docx", "zip").contains(normalized)) {
            throw invalid("模板扩展名不在允许列表中");
        }
        return normalized;
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /** 已验证并可写入私有对象存储的文件。 */
    public record ValidatedFile(String originalFilename, String extension, String contentType,
                                byte[] content, String sha256) {
    }
}

package com.scott.payment.admin.support.template;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 系统内置模板初始化使用的只读内存文件。
 *
 * <p>仍然通过与页面上传完全相同的 {@link TemplateFileSecurityValidator} 校验链路，
 * 不允许初始化流程绕过文件签名、宏、压缩包或大小限制。</p>
 */
public final class InMemoryTemplateMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryTemplateMultipartFile(String name, String originalFilename,
                                         String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(content, content.length);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(content);
        }
    }
}

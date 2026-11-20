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

    /** 保留内置资源的表单字段名，使其复用普通上传的校验入口。 */
    @Override
    public String getName() {
        return name;
    }

    /** 返回参与扩展名及文件名安全校验的内置资源名。 */
    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    /** 提供内置资源声明类型，仍需内容签名验证，不能据此信任文件。 */
    @Override
    public String getContentType() {
        return contentType;
    }

    /** 按实际快照字节判断空文件，不依赖调用方声明长度。 */
    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    /** 使用内存快照的真实长度参与上传大小限制。 */
    @Override
    public long getSize() {
        return content.length;
    }

    /** 返回字节副本，防止调用方修改已进入安全校验链的原始快照。 */
    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(content, content.length);
    }

    /** 每次提供独立快照流，供签名、压缩包及安全校验重复读取。 */
    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    /** 将快照写入目标文件并关闭流；此适配本身不替代上传安全校验。 */
    @Override
    public void transferTo(File destination) throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(content);
        }
    }
}

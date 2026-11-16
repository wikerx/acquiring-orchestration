package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.system.FileTemplateDTOs.Download;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 旧模板 URL 的迁移桥：模板中心已有发布版本时优先返回受控资产，否则由调用方执行旧生成器。
 */
@Component
public class LegacyFileTemplateBridge {

    private final AdminFileTemplateApplicationService fileTemplateApplicationService;

    public LegacyFileTemplateBridge(AdminFileTemplateApplicationService fileTemplateApplicationService) {
        this.fileTemplateApplicationService = fileTemplateApplicationService;
    }

    public boolean writeIfPublished(String templateCode, HttpServletResponse response) {
        return fileTemplateApplicationService.downloadCurrentIfPublished(
                        templateCode, LocaleContextHolder.getLocale().toLanguageTag())
                .map(file -> write(response, file))
                .orElse(false);
    }

    private boolean write(HttpServletResponse response, Download file) {
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFilename);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        try {
            response.getOutputStream().write(file.content());
            response.flushBuffer();
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("写入系统模板下载响应失败", exception);
        }
    }
}

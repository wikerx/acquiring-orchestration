package com.scott.payment.merchant.api.system;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.template.ManagedFileTemplateEndpoint;
import com.scott.payment.merchant.application.system.MerchantFileTemplateApplicationService;
import com.scott.payment.merchant.application.system.MerchantFileTemplateApplicationService.Download;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 商户系统文件模板统一下载入口；模板维护仅在管理系统开放。 */
@RestController
@RequestMapping("/merchant/system/file-templates")
public class MerchantFileTemplateController {

    private final MerchantFileTemplateApplicationService applicationService;

    public MerchantFileTemplateController(MerchantFileTemplateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{templateCode}/download")
    @RequiresPermission("merchant:file-template:download")
    @ManagedFileTemplateEndpoint("DYNAMIC")
    public ResponseEntity<byte[]> download(@PathVariable("templateCode") String templateCode,
                                           @RequestParam(value = "locale", required = false) String locale) {
        Download file = applicationService.download(templateCode, locale);
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }
}

package com.scott.payment.component.web.upload;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 图片按鉴权后的二进制响应返回，不暴露 Bucket、对象键或会话令牌。 */
public final class ImageResponses {
    private ImageResponses() { }
    /** 以禁止缓存、禁止类型嗅探的 PNG 响应返回已授权内容，不提供对象存储直链。 */
    public static ResponseEntity<byte[]> png(byte[] content) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.png\"")
                .body(content);
    }
}

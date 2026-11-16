package com.scott.payment.component.web.upload;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.ManagedImageReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 图片正文写入与数据库事务联动：回滚清理新图，提交后清理旧图。 */
@Slf4j
public class ManagedImageStore {
    private final ObjectProvider<ImageObjectStorage> provider;
    public ManagedImageStore(ObjectProvider<ImageObjectStorage> provider) { this.provider = provider; }

    public String write(String scope, byte[] content) {
        if (!scope.matches("(avatar|channel)/[0-9]+") || content.length == 0 || content.length > 2097152
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("managed image writes require a transaction and bounded content");
        }
        ImageObjectStorage storage = storage();
        String key = "images/" + scope + "/" + UUID.randomUUID().toString().replace("-", "") + "-" + sha256(content) + ".png";
        // 即使对象存储在响应前断连，也尝试清理可能已写入的对象。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(storage, key);
            }
        });
        storage.put(key, content);
        return key;
    }

    public byte[] read(String key, String scope) {
        if (!ManagedImageReference.valid(key) || !key.startsWith("images/" + scope + "/")) throw missing();
        byte[] content = storage().get(key);
        String expected = key.substring(key.lastIndexOf('-') + 1, key.length() - 4);
        if (content.length > 2097152 || !MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                sha256(content).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "图片完整性校验失败");
        }
        return content;
    }

    public void deleteAfterCommit(String key) {
        if (!ManagedImageReference.valid(key)) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("image cleanup requires a transaction");
        ImageObjectStorage storage = storage();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(storage, key); }
        });
    }

    private ImageObjectStorage storage() {
        ImageObjectStorage storage = provider.getIfAvailable();
        if (storage == null) throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "图片存储暂不可用，请稍后重试");
        return storage;
    }
    private void deleteQuietly(ImageObjectStorage storage, String key) {
        try { storage.delete(key); }
        catch (RuntimeException exception) { log.error("图片对象清理失败, eventCode: IMAGE_OBJECT_CLEANUP_FAILED, imageVersion: {}", ManagedImageReference.version(key)); }
    }
    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static ServiceException missing() { return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "图片不存在或不可访问"); }
}

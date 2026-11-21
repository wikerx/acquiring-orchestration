package com.scott.payment.component.db.auth.support;

import org.springframework.dao.DuplicateKeyException;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Consumer;

/** 认证对象编码的技术生成器；调用方决定命名空间，数据库唯一约束负责最终防重。 */
public final class GeneratedAuthCode {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SUFFIX_LENGTH = 6;
    private static final int MAX_CODE_LENGTH = 80;
    private static final int MAX_ATTEMPTS = 5;

    private GeneratedAuthCode() {
    }

    /** 仅重试唯一键碰撞；其他数据库错误直接交由拥有者事务回滚。回调只执行单条对象插入。 */
    public static String insert(String prefix, Consumer<String> insert) {
        Objects.requireNonNull(insert, "insert is required");
        if (prefix == null || !prefix.endsWith("_") || prefix.length() + SUFFIX_LENGTH > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("generated auth code prefix exceeds its contract");
        }
        for (int attempt = 0; ; attempt++) {
            StringBuilder code = new StringBuilder(prefix);
            for (int index = 0; index < SUFFIX_LENGTH; index++) code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            String generated = code.toString();
            try {
                insert.accept(generated);
                return generated;
            } catch (DuplicateKeyException exception) {
                if (attempt + 1 >= MAX_ATTEMPTS) throw exception;
            }
        }
    }
}

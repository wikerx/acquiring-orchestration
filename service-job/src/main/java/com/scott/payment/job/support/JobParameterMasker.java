package com.scott.payment.job.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobParameterMasker
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务参数脱敏工具
 * @status : create
 */
public final class JobParameterMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "secret",
            "token",
            "privateKey",
            "apiSecret",
            "signKey",
            "jwt",
            "cvv",
            "cardNo"
    );

    /**
     * 脱敏值常量，统一 {@code JobParameterMasker} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MASKED_VALUE = "******";

    private JobParameterMasker() {
    }

    /**
     * 脱敏 JSON 文本。
     *
     * @param paramsJson 原始参数 JSON
     * @return 脱敏后的 JSON；解析失败时返回原文
     */
    public static String mask(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return paramsJson;
        }
        try {
            Object value = JSON.parse(paramsJson);
            maskObject(value);
            return JSON.toJSONString(value);
        } catch (Exception exception) {
            return paramsJson;
        }
    }

    /**
     * 递归脱敏对象树。
     *
     * @param value JSON 对象树
     */
    private static void maskObject(Object value) {
        if (value instanceof JSONObject jsonObject) {
            for (String key : jsonObject.keySet()) {
                Object child = jsonObject.get(key);
                if (isSensitiveKey(key)) {
                    jsonObject.put(key, MASKED_VALUE);
                } else {
                    maskObject(child);
                }
            }
        }
        if (value instanceof JSONArray jsonArray) {
            for (Object child : jsonArray) {
                maskObject(child);
            }
        }
    }

    /**
     * 判断当前字段是否敏感。
     *
     * @param key JSON 字段名
     * @return true 表示需要脱敏
     */
    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}

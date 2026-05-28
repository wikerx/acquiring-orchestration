package com.sinopay.payment.component.core.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JsonUtils
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 统一 JSON 序列化与反序列化工具
 * @status : create
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    public static String toJsonString(Object object) {
        return JSON.toJSONString(object);
    }

    public static <T> T parseObject(String json, Class<T> clazz) {
        if (isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, typeReference.getType());
    }

    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, clazz);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

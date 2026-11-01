package com.scott.payment.component.core.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.util.Collections;
import java.util.List;

/**
 * 统一 JSON 序列化与反序列化工具，封装 fastjson2 的基础调用，避免业务代码散落使用多套 JSON 实现。
 */
public final class JsonUtils {

    /**
     * 禁止实例化统一 JSON 工具类。
     */
    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param object 待序列化对象
     * @return JSON 字符串
     */
    public static String toJsonString(Object object) {
        return JSON.toJSONString(object);
    }

    /**
     * 将 JSON 字符串反序列化为指定类型对象。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 目标对象，空字符串返回 null
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    /**
     * 将 JSON 字符串反序列化为带泛型信息的对象。
     *
     * @param json          JSON 字符串
     * @param typeReference 泛型类型引用
     * @param <T>           目标泛型
     * @return 目标对象，空字符串返回 null
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, typeReference.getType());
    }

    /**
     * 将 JSON 数组字符串反序列化为对象列表。
     *
     * @param json  JSON 数组字符串
     * @param clazz 列表元素类型
     * @param <T>   列表元素泛型
     * @return 目标对象列表，空字符串返回空列表
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, clazz);
    }

    /**
     * 判断 JSON 文本是否为空，供各反序列化入口统一处理空输入。
     *
     * @param value 待检查文本
     * @return null、空串或纯空白时返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

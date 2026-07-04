package com.scott.payment.component.core.json;

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

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param object 待序列化对象
     * @return JSON 字符串
     */
    /**
     * 转换收单支付数据结构，避免数据库实体直接暴露到外部接口。
     * @param object 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param json 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param clazz 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param json 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param typeReference 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param json 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param clazz 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

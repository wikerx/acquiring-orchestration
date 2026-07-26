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
 * @description : 统一 JSON 序列化与反序列化工具，封装 fastjson2 的基础调用，避免业务代码散落使用多套 JSON 实现。
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
     * 判断 is Blank 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

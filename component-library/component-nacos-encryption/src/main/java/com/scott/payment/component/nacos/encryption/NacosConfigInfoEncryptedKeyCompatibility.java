package com.scott.payment.component.nacos.encryption;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NacosConfigInfoEncryptedKeyCompatibility
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : Nacos 2.3.2 服务端配置恢复兼容层；修复 config_info Mapper 在读取密文正文时遗漏
 * encrypted_data_key，导致配置中心重启后无法解密 cipher-* DataId 的问题。仅在检测到 Config Server 时启用，
 * 不介入普通 Nacos 客户端，也不修改配置内容、密钥或数据库数据。
 * @status : create
 */
final class NacosConfigInfoEncryptedKeyCompatibility {

    private static final String CONFIG_SERVER_MARKER =
            "com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService";
    private static final String MAPPER_MANAGER_CLASS = "com.alibaba.nacos.plugin.datasource.MapperManager";
    private static final String CONFIG_INFO_MAPPER_INTERFACE =
            "com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper";
    private static final String CONFIG_INFO_TABLE = "config_info";
    private static final String CONTENT_COLUMN = "content";
    private static final String ENCRYPTED_DATA_KEY_COLUMN = "encrypted_data_key";

    private NacosConfigInfoEncryptedKeyCompatibility() {
    }

    /**
     * 检测当前进程是否为 Nacos Config Server，并对所有已加载数据源的 config_info Mapper 安装兼容代理。
     * 普通业务服务仅携带 Nacos 客户端，不存在服务端标记类，因此直接跳过。
     */
    static void installIfRequired() {
        ClassLoader classLoader = AcquiringAesGcmEncryptionPlugin.class.getClassLoader();
        if (!isPresent(CONFIG_SERVER_MARKER, classLoader)) {
            return;
        }
        try {
            Class<?> mapperManagerClass = Class.forName(MAPPER_MANAGER_CLASS, true, classLoader);
            Class<?> mapperInterface = Class.forName(CONFIG_INFO_MAPPER_INTERFACE, false, classLoader);
            Field mapperField = mapperManagerClass.getField("MAPPER_SPI_MAP");
            Object value = mapperField.get(null);
            if (!(value instanceof Map)) {
                throw new IllegalStateException("Nacos config_info mapper registry is unavailable");
            }
            int installed = installMapperProxies((Map<?, ?>) value, mapperInterface, classLoader);
            if (installed == 0) {
                throw new IllegalStateException("Nacos config_info mapper compatibility was not installed");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Nacos config_info encrypted key compatibility installation failed",
                    exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int installMapperProxies(Map<?, ?> registry, Class<?> mapperInterface, ClassLoader classLoader) {
        int installed = 0;
        for (Object dataSourceEntryObject : new ArrayList<>(registry.entrySet())) {
            Map.Entry dataSourceEntry = (Map.Entry) dataSourceEntryObject;
            if (!(dataSourceEntry.getValue() instanceof Map)) {
                continue;
            }
            Map tableMappers = (Map) dataSourceEntry.getValue();
            synchronized (tableMappers) {
                Object mapper = tableMappers.get(CONFIG_INFO_TABLE);
                if (mapper == null || isCompatibilityProxy(mapper)) {
                    continue;
                }
                if (!mapperInterface.isInstance(mapper)) {
                    throw new IllegalStateException("Unexpected Nacos config_info mapper type: "
                            + mapper.getClass().getName());
                }
                Object proxy = Proxy.newProxyInstance(classLoader, new Class<?>[]{mapperInterface},
                        new ConfigInfoMapperInvocationHandler(mapper));
                tableMappers.put(CONFIG_INFO_TABLE, proxy);
                installed++;
            }
        }
        return installed;
    }

    private static boolean isCompatibilityProxy(Object mapper) {
        return Proxy.isProxyClass(mapper.getClass())
                && Proxy.getInvocationHandler(mapper) instanceof ConfigInfoMapperInvocationHandler;
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    /**
     * 只改写选择了配置正文但遗漏加密数据密钥的查询；已包含字段或不读取正文的 SQL 保持不变。
     */
    static String includeEncryptedDataKey(String sql) {
        if (sql == null) {
            return null;
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        int fromIndex = normalized.indexOf(" from ");
        if (fromIndex < 0
                || !normalized.substring(0, fromIndex).contains(CONTENT_COLUMN)
                || normalized.substring(0, fromIndex).contains(ENCRYPTED_DATA_KEY_COLUMN)) {
            return sql;
        }
        return sql.substring(0, fromIndex) + "," + ENCRYPTED_DATA_KEY_COLUMN + sql.substring(fromIndex);
    }

    private static final class ConfigInfoMapperInvocationHandler implements InvocationHandler {

        private final Object delegate;

        private ConfigInfoMapperInvocationHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
            if (result == null || !"com.alibaba.nacos.plugin.datasource.model.MapperResult"
                    .equals(result.getClass().getName())) {
                return result;
            }
            Method getSql = result.getClass().getMethod("getSql");
            Method setSql = result.getClass().getMethod("setSql", String.class);
            String originalSql = (String) getSql.invoke(result);
            String compatibleSql = includeEncryptedDataKey(originalSql);
            if (compatibleSql != null && !compatibleSql.equals(originalSql)) {
                setSql.invoke(result, compatibleSql);
            }
            return result;
        }
    }
}

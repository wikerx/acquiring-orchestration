package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 当前 Admin 节点的服务器运行信息响应。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerMonitorResponse {

    /**
     * CPU 和操作系统负载信息。
     */
    private CpuInfo cpu;

    /**
     * JVM 内存和版本信息。
     */
    private JvmInfo jvm;

    /**
     * 当前节点操作系统信息。
     */
    private SystemInfo system;

    /**
     * 当前 JVM 运行时间信息。
     */
    private RuntimeInfo runtime;

    /**
     * 当前节点首个文件系统根目录的容量信息。
     */
    private DiskInfo disk;

    /**
     * CPU 和操作系统负载信息。
     */
    @Data
    public static class CpuInfo {

        /**
         * JVM 可用处理器数量。
         */
        private Integer availableProcessors;

        /**
         * 操作系统最近一分钟的平均负载。
         */
        private Double systemLoadAverage;

        /**
         * 操作系统架构。
         */
        private String arch;

        /**
         * 操作系统名称。
         */
        private String name;

        /**
         * 操作系统版本。
         */
        private String version;
    }

    /**
     * JVM 内存和版本信息。
     */
    @Data
    public static class JvmInfo {

        /**
         * JVM 最大可用内存展示值。
         */
        private String max;

        /**
         * JVM 当前已分配内存展示值。
         */
        private String total;

        /**
         * JVM 当前空闲内存展示值。
         */
        private String free;

        /**
         * JVM 当前已使用内存展示值。
         */
        private String used;

        /**
         * Java 运行时版本。
         */
        private String javaVersion;

        /**
         * Java 运行时安装目录。
         */
        private String javaHome;
    }

    /**
     * 当前节点操作系统信息。
     */
    @Data
    public static class SystemInfo {

        /**
         * 操作系统名称。
         */
        private String osName;

        /**
         * 操作系统架构。
         */
        private String osArch;

        /**
         * 运行 Admin 服务的系统账号名称。
         */
        private String userName;
    }

    /**
     * 当前 JVM 运行时间信息。
     */
    @Data
    public static class RuntimeInfo {

        /**
         * 本地化的 JVM 已运行时长。
         */
        private String uptime;

        /**
         * JVM 启动时间的 Unix 毫秒值。
         */
        private Long startTime;
    }

    /**
     * 文件系统容量信息。
     */
    @Data
    public static class DiskInfo {

        /**
         * 文件系统根路径。
         */
        private String path;

        /**
         * 文件系统总容量展示值。
         */
        private String total;

        /**
         * 文件系统可用容量展示值。
         */
        private String free;

        /**
         * 文件系统已使用容量展示值。
         */
        private String used;

        /**
         * 文件系统使用率展示值。
         */
        private String usagePercent;
    }
}

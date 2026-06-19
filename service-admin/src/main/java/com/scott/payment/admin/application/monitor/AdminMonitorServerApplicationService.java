package com.scott.payment.admin.application.monitor;

import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务监控应用服务。
 */
@Service
public class AdminMonitorServerApplicationService {

    /**
     * 采集当前节点的 CPU、JVM、系统和磁盘运行信息。
     *
     * @return 服务器监控摘要
     */
    public Map<String, Object> serverInfo() {
        Map<String, Object> result = new LinkedHashMap<>();

        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        cpu.put("systemLoadAverage", round(operatingSystemMXBean.getSystemLoadAverage(), 2));
        cpu.put("arch", operatingSystemMXBean.getArch());
        cpu.put("name", operatingSystemMXBean.getName());
        cpu.put("version", operatingSystemMXBean.getVersion());
        result.put("cpu", cpu);

        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("max", bytesToMB(runtime.maxMemory()) + " MB");
        jvm.put("total", bytesToMB(runtime.totalMemory()) + " MB");
        jvm.put("free", bytesToMB(runtime.freeMemory()) + " MB");
        jvm.put("used", bytesToMB(runtime.totalMemory() - runtime.freeMemory()) + " MB");
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("javaHome", System.getProperty("java.home"));
        result.put("jvm", jvm);

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("osName", System.getProperty("os.name"));
        system.put("osArch", System.getProperty("os.arch"));
        system.put("userName", System.getProperty("user.name"));
        result.put("system", system);

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtimeInfo = new LinkedHashMap<>();
        runtimeInfo.put("uptime", formatUptime(runtimeMXBean.getUptime()));
        runtimeInfo.put("startTime", runtimeMXBean.getStartTime());
        result.put("runtime", runtimeInfo);

        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            File root = roots[0];
            Map<String, Object> disk = new LinkedHashMap<>();
            disk.put("path", root.getAbsolutePath());
            disk.put("total", bytesToGB(root.getTotalSpace()) + " GB");
            disk.put("free", bytesToGB(root.getFreeSpace()) + " GB");
            disk.put("used", bytesToGB(root.getTotalSpace() - root.getFreeSpace()) + " GB");
            disk.put("usagePercent", usagePercent(root));
            result.put("disk", disk);
        }
        return result;
    }

    /**
     * 将字节数转换为 MB，供监控展示使用。
     *
     * @param bytes 字节数
     * @return MB
     */
    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * 将字节数转换为 GB，供磁盘展示使用。
     *
     * @param bytes 字节数
     * @return GB
     */
    private long bytesToGB(long bytes) {
        return bytes / (1024 * 1024 * 1024);
    }

    /**
     * 对监控数值做四舍五入，避免原始浮点值不利于前端展示。
     *
     * @param value  原始值
     * @param places 保留位数
     * @return 舍入结果
     */
    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 把毫秒级运行时长转换为易读文本。
     *
     * @param milliseconds 运行时长
     * @return 文本化时长
     */
    private String formatUptime(long milliseconds) {
        long days = milliseconds / (24 * 60 * 60 * 1000);
        long hours = (milliseconds % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);
        return days + "天 " + hours + "小时 " + minutes + "分钟";
    }

    /**
     * 计算磁盘使用率。
     *
     * @param root 磁盘根目录
     * @return 百分比文本
     */
    private String usagePercent(File root) {
        long total = root.getTotalSpace();
        if (total == 0) {
            return "N/A";
        }
        long used = total - root.getFreeSpace();
        return BigDecimal.valueOf(used * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP) + "%";
    }
}

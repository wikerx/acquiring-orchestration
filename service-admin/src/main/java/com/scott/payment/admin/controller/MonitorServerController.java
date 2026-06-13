package com.scott.payment.admin.controller;

import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorServerController
 * @date : 2026-06-12 17:30
 * @email : scott_x@163.com
 * @description : 服务监控控制器，提供 CPU、内存、JVM、磁盘等服务器运行信息
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor")
public class MonitorServerController {

    /**
     * 获取服务器运行信息。
     *
     * <p>返回 CPU 核心数与系统负载、JVM 内存使用、操作系统信息、运行时长和磁盘使用情况。
     *
     * @return 服务器运行信息 Map
     */
    @GetMapping("/server")
    @RequiresPermission("system:server:list")
    public CommonResult<Map<String, Object>> serverInfo() {
        Map<String, Object> result = new LinkedHashMap<>();

        // CPU 信息
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        cpu.put("systemLoadAverage", round(osBean.getSystemLoadAverage(), 2));
        cpu.put("arch", osBean.getArch());
        cpu.put("name", osBean.getName());
        cpu.put("version", osBean.getVersion());
        result.put("cpu", cpu);

        // JVM 内存信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("max", bytesToMB(runtime.maxMemory()) + " MB");
        jvm.put("total", bytesToMB(runtime.totalMemory()) + " MB");
        jvm.put("free", bytesToMB(runtime.freeMemory()) + " MB");
        jvm.put("used", bytesToMB(runtime.totalMemory() - runtime.freeMemory()) + " MB");
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("javaHome", System.getProperty("java.home"));
        result.put("jvm", jvm);

        // 操作系统信息
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("osName", System.getProperty("os.name"));
        sys.put("osArch", System.getProperty("os.arch"));
        sys.put("userName", System.getProperty("user.name"));
        result.put("system", sys);

        // 运行时长信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> run = new LinkedHashMap<>();
        long uptimeMs = runtimeBean.getUptime();
        run.put("uptime", formatUptime(uptimeMs));
        run.put("startTime", runtimeBean.getStartTime());
        result.put("runtime", run);

        // 磁盘信息（取第一个根路径）
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

        return success(result);
    }

    /**
     * 字节转 MB。
     *
     * @param bytes 字节数
     * @return MB 数值
     */
    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * 字节转 GB。
     *
     * @param bytes 字节数
     * @return GB 数值
     */
    private long bytesToGB(long bytes) {
        return bytes / (1024 * 1024 * 1024);
    }

    /**
     * 安全四舍五入。
     *
     * @param value  原始值
     * @param places 小数位数
     * @return 四舍五入后的值
     */
    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 格式化运行时长。
     *
     * @param ms 毫秒数
     * @return 人类可读的运行时长字符串
     */
    private String formatUptime(long ms) {
        long days = ms / (24 * 60 * 60 * 1000);
        long hours = (ms % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (ms % (60 * 60 * 1000)) / (60 * 1000);
        return days + "天 " + hours + "小时 " + minutes + "分钟";
    }

    /**
     * 计算磁盘使用率百分比。
     *
     * @param root 磁盘根目录
     * @return 使用率百分比字符串
     */
    private String usagePercent(File root) {
        long total = root.getTotalSpace();
        if (total == 0) {
            return "N/A";
        }
        long used = total - root.getFreeSpace();
        return BigDecimal.valueOf(used * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                + "%";
    }
}

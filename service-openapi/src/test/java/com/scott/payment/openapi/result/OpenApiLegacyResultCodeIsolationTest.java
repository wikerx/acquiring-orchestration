package com.scott.payment.openapi.result;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI V1.3 与内部旧结果枚举之间的依赖隔离门禁。
 *
 * <p>生产代码只能通过统一映射器兼容旧码，禁止重新直接依赖内部 {@code ApiResultEnum}。</p>
 */
class OpenApiLegacyResultCodeIsolationTest {

    /**
     * 旧码兼容只允许存在于统一投影映射器的字符串映射中；生产代码不得重新导入内部结果枚举。
     */
    @Test
    void shouldNotReferenceLegacyResultEnumFromOpenApiProductionSources() throws IOException {
        Path sourceRoot = locateProductionSourceRoot();
        List<String> violations;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsLegacyEnumReference(path))
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .as("service-openapi production sources referencing ApiResultEnum")
                .isEmpty();
    }

    /**
     * 兼容从仓库根目录或模块目录执行测试时的源码根目录定位。
     */
    private Path locateProductionSourceRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path moduleSource = workingDirectory.resolve("service-openapi/src/main/java");
        Path directSource = workingDirectory.resolve("src/main/java");
        if (Files.isDirectory(moduleSource)) {
            return moduleSource;
        }
        if (Files.isDirectory(directSource)) {
            return directSource;
        }
        throw new IllegalStateException("Unable to locate service-openapi production sources from "
                + workingDirectory);
    }

    /**
     * 读取单个生产源码并检测被禁止的旧枚举依赖。
     */
    private boolean containsLegacyEnumReference(Path sourceFile) {
        try {
            return Files.readString(sourceFile).contains("ApiResultEnum");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + sourceFile, exception);
        }
    }
}

package com.scott.payment.component.db.constant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceAnnotationArchitectureTest
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 数据源路由注解全仓架构门禁，禁止类级路由及无法被代理的方法级路由。
 * @status : create
 */
class DataSourceAnnotationArchitectureTest {

    /** 类级注解后允许存在其他 Spring 注解，但最终必须紧邻 Java 类型声明。 */
    private static final Pattern TYPE_LEVEL_DS = Pattern.compile(
            "(?m)^\\s*@DS\\([^\\n]+\\)\\R(?:\\s*@[^\\n]+\\R)*"
                    + "\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+)?"
                    + "(?:class|interface|record|enum)\\b");

    /** 顶层 Java 类型声明，用于区分类中的公开代理方法和接口中的隐式公开方法。 */
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+)?"
                    + "(class|interface|record|enum)\\b");

    /** 方法声明的最小结构校验，覆盖多行参数和可选 throws 子句。 */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?s)^.+\\([^;{}]*\\)\\s*(?:throws\\s+[^;{]+)?[;{]$");

    /** 扫描所有生产 Java 源码，阻止类级数据源路由重新进入仓库。 */
    @Test
    void productionTypesMustNotDeclareDataSourceRouting() throws IOException {
        Path projectRoot = findProjectRoot();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> collectViolation(projectRoot, path, violations));
        }
        assertThat(violations)
                .as("@DS must be declared on public methods, never on production types")
                .isEmpty();
    }

    /**
     * 验证生产代码中的路由注解只声明在可被代理的实例方法上。
     *
     * @throws IOException 生产源码无法读取时测试失败
     */
    @Test
    void productionDataSourceRoutingMustTargetProxyableMethods() throws IOException {
        Path projectRoot = findProjectRoot();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> collectMethodTargetViolations(projectRoot, path, violations));
        }
        assertThat(violations)
                .as("@DS must target public instance methods or implicitly public interface methods")
                .isEmpty();
    }

    /** 从 Maven 根目录或子模块测试目录向上定位当前多模块工程。 */
    private Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("service-admin"))
                    && Files.isDirectory(current.resolve("component-library"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("acquiring-orchestration project root was not found");
    }

    /** 读取单个生产源码并记录类级路由违规路径。 */
    private void collectViolation(Path projectRoot, Path source, List<String> violations) {
        try {
            if (TYPE_LEVEL_DS.matcher(Files.readString(source)).find()) {
                violations.add(projectRoot.relativize(source).toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect source: " + source, exception);
        }
    }

    /**
     * 读取单个生产源码并校验每个 {@code @DS} 后的声明类型和代理可见性。
     *
     * @param projectRoot Maven 多模块工程根目录
     * @param source      待检查生产源码
     * @param violations  违规信息集合
     */
    private void collectMethodTargetViolations(Path projectRoot, Path source, List<String> violations) {
        try {
            String content = Files.readString(source);
            List<String> lines = Arrays.asList(content.split("\\R", -1));
            boolean interfaceType = isTopLevelInterface(content);
            for (int index = 0; index < lines.size(); index++) {
                if (!lines.get(index).trim().startsWith("@DS(")) {
                    continue;
                }
                String declaration = findAnnotatedDeclaration(lines, index + 1);
                if (!isMethodDeclaration(declaration)
                        || (!interfaceType && !isPublicInstanceMethod(declaration))) {
                    violations.add(projectRoot.relativize(source) + ":" + (index + 1));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect source: " + source, exception);
        }
    }

    /** 判断源码顶层声明是否为接口，接口方法无需显式书写 public。 */
    private boolean isTopLevelInterface(String content) {
        var matcher = TOP_LEVEL_TYPE.matcher(content);
        return matcher.find() && "interface".equals(matcher.group(1));
    }

    /**
     * 跳过其他方法注解并提取 {@code @DS} 对应的完整声明。
     *
     * @param lines      源码行
     * @param startIndex {@code @DS} 下一行下标
     * @return 方法或类型声明；无法提取时返回空字符串
     */
    private String findAnnotatedDeclaration(List<String> lines, int startIndex) {
        int index = startIndex;
        while (index < lines.size()) {
            String trimmed = lines.get(index).trim();
            if (trimmed.isEmpty()) {
                index++;
                continue;
            }
            if (trimmed.startsWith("@")) {
                index = skipAnnotation(lines, index);
                continue;
            }
            StringBuilder declaration = new StringBuilder(trimmed);
            while (!endsDeclaration(declaration.toString()) && ++index < lines.size()) {
                declaration.append(' ').append(lines.get(index).trim());
            }
            return declaration.toString();
        }
        return "";
    }

    /**
     * 跳过单行或多行 Java 注解。
     *
     * @param lines 源码行
     * @param index 注解起始下标
     * @return 注解结束后的首行下标
     */
    private int skipAnnotation(List<String> lines, int index) {
        int parenthesisDepth = 0;
        boolean hasParenthesis = false;
        do {
            String line = lines.get(index);
            for (int offset = 0; offset < line.length(); offset++) {
                char current = line.charAt(offset);
                if (current == '(') {
                    parenthesisDepth++;
                    hasParenthesis = true;
                } else if (current == ')') {
                    parenthesisDepth--;
                }
            }
            index++;
        } while (index < lines.size() && hasParenthesis && parenthesisDepth > 0);
        return index;
    }

    /** 判断已拼接文本是否到达方法声明的分号或方法体起始大括号。 */
    private boolean endsDeclaration(String declaration) {
        return declaration.endsWith(";") || declaration.endsWith("{");
    }

    /** 判断注解目标是否具备方法声明结构。 */
    private boolean isMethodDeclaration(String declaration) {
        return METHOD_DECLARATION.matcher(declaration).matches();
    }

    /** 判断类中的注解目标是否为可被 Spring AOP 代理的公开实例方法。 */
    private boolean isPublicInstanceMethod(String declaration) {
        return declaration.startsWith("public ") && !declaration.matches("^public\\s+static\\b.*");
    }
}

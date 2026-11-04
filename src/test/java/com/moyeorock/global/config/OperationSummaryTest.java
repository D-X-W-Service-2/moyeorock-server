package com.moyeorock.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 엔드포인트에 {@code @Operation(summary)}가 있는지 확인한다(api-conventions.md §9). Swagger UI에서 엔드포인트가
 * 요약 없이 메서드 이름으로 보이는 것을 막는다. 스프링 컨텍스트를 띄우지 않아 Docker 없이 돈다.
 */
class OperationSummaryTest {

    @Test
    @DisplayName("@RestController의 모든 매핑 메서드에 비어 있지 않은 @Operation(summary)가 있다")
    void everyEndpointHasOperationSummary() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents("com.moyeorock")) {
            Class<?> controller = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            if (isTestClass(controller)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                checked++;
                Operation operation = method.getAnnotation(Operation.class);
                if (operation == null || operation.summary().isBlank()) {
                    missing.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(checked).as("검사한 엔드포인트 수").isGreaterThan(0);
        assertThat(missing).as("@Operation(summary)가 없는 엔드포인트").isEmpty();
    }

    // 테스트 전용 컨트롤러(예: 예외 응답 확인용 Probe)는 실제 API가 아니라 검사에서 뺀다.
    private static boolean isTestClass(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("/classes/java/test");
    }
}

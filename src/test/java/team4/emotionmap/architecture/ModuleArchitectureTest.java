package team4.emotionmap.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** 공유 DB와 별개로 Java 모듈의 내부 타입 접근과 순환 의존성을 제한한다. */
class ModuleArchitectureTest {

    private static final String ROOT = "team4.emotionmap";
    private static final List<String> MODULES =
            List.of("account", "memory", "place", "letter", "report", "media", "platform");
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT);

    @Test
    void productionClassesBelongToDeclaredModules() {
        classes().that().doNotHaveFullyQualifiedName(ROOT + ".EmotionMapApplication")
                .should().resideInAnyPackage(MODULES.stream()
                        .map(module -> ROOT + "." + module + "..")
                        .toArray(String[]::new))
                .because("애플리케이션 진입점 외의 코드는 소유 모듈에 위치해야 한다")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void modulesHaveNoDependencyCycles() {
        slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    @TestFactory
    Stream<DynamicTest> modulesOnlyAccessTheirOwnTypesAndExplicitPublicContracts() {
        return MODULES.stream().map(module -> dynamicTest(module, () -> {
            DescribedPredicate<JavaClass> allowedDependencies =
                    resideOutsideOfPackage(ROOT + "..")
                            .or(resideInAPackage(ROOT + "." + module + ".."));
            if (module.equals("account")) {
                allowedDependencies = allowedDependencies.or(DescribedPredicate.describe(
                        "계정 모듈에 공개한 JWT 발급 API",
                        type -> type.getName().equals(ROOT + ".platform.security.JwtTokenProvider")));
            }
            classes().that().resideInAPackage(ROOT + "." + module + "..")
                    .should().onlyDependOnClassesThat(allowedDependencies)
                    .because("다른 모듈의 엔티티·저장소·내부 서비스를 직접 사용하지 않는다")
                    .check(PRODUCTION_CLASSES);
        }));
    }
}

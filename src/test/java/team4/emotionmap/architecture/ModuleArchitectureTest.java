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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * 공유 DB와 별개로 Java 모듈의 내부 타입 접근과 순환 의존성을 제한한다.
 *
 * <p>허용되는 모듈 간 계약(WORK_PLAN §8·§13.1, SHARED_CONTRACTS §3):
 * <ul>
 *   <li>{@code contracts} — 공동 계약(포트·값 객체·오류). 모든 모듈이 의존 가능. 자신은 내부 모듈에 의존하지 않는다.</li>
 *   <li>{@code platform} — 보안·웹·OpenAPI 기반. contracts 에만 의존하고 업무 모듈에는 의존하지 않는다.</li>
 *   <li>{@code account → platform.security} — JWT·현재 계정 검사와 공통 비밀번호 인코더의 명시 계약만 사용한다.</li>
 * </ul>
 */
class ModuleArchitectureTest {

    private static final String ROOT = "team4.emotionmap";
    private static final String CONTRACTS = "contracts";
    private static final List<String> MODULES =
            List.of("account", "memory", "place", "letter", "report", "media", "catalog", "notification", "platform", CONTRACTS);
    // Explicit same-DB contracts used by cross-domain transactions. No wildcard
    // package access: adding a dependency still requires reviewing this boundary.
    private static final Map<String, List<String>> PUBLIC_CONTRACTS = Map.of(
            "account", List.of("platform.security.JwtTokenProvider", "platform.security.AccountAccessGuard",
                    "platform.security.PasswordHashConfiguration"),
            "memory", List.of("account.User", "account.UserRepository", "account.AccountAccessService",
                    "media.ImageStorageService", "media.ImageUpload", "media.ImageUploadService", "media.StoredImage",
                    "place.Place", "place.PlaceRepository", "place.PlaceCategory", "place.PlaceCategoryRepository",
                    "place.PlaceCategorySource", "place.NaverCategoryMapper"),
            "notification", List.of("account.User", "account.AccountAccessService",
                    "place.Place", "place.PlaceRepository", "memory.MemoryRepository"),
            "media", List.of("account.User", "account.UserRepository", "account.AccountAccessService",
                    "platform.web.ApiErrorWriter"),
            "letter", List.of("account.AccountAccessService", "account.User", "account.UserRepository",
                    "media.ImageStorageService", "memory.Memory",
                    "memory.MemoryAccessService", "memory.MemoryCategory", "memory.MemoryCategoryRepository",
                    "memory.MemoryRepository", "memory.MemoryReadAccess"),
            "report", List.of("account.AccountAccessService", "account.User",
                    "memory.MemoryAccessService", "memory.Memory"));
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

    @Test
    void contractsDependOnNothingInsideTheApplication() {
        classes().that().resideInAPackage(ROOT + "." + CONTRACTS + "..")
                .should().onlyDependOnClassesThat(
                        resideOutsideOfPackage(ROOT + "..").or(resideInAPackage(ROOT + "." + CONTRACTS + "..")))
                .because("공동 계약은 구현 모듈을 모른다")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void platformDoesNotDependOnBusinessModules() {
        classes().that().resideInAPackage(ROOT + ".platform..")
                .should().onlyDependOnClassesThat(
                        resideOutsideOfPackage(ROOT + "..")
                                .or(resideInAPackage(ROOT + ".platform.."))
                                .or(resideInAPackage(ROOT + "." + CONTRACTS + "..")))
                .because("platform 은 업무 모듈에 의존하지 않는다")
                .check(PRODUCTION_CLASSES);
    }

    @TestFactory
    Stream<DynamicTest> modulesOnlyAccessTheirOwnTypesAndExplicitPublicContracts() {
        return MODULES.stream().filter(m -> !m.equals(CONTRACTS)).map(module -> dynamicTest(module, () -> {
            DescribedPredicate<JavaClass> allowedDependencies =
                    resideOutsideOfPackage(ROOT + "..")
                            .or(resideInAPackage(ROOT + "." + module + ".."))
                            .or(resideInAPackage(ROOT + "." + CONTRACTS + ".."));
            List<String> contracts = PUBLIC_CONTRACTS.getOrDefault(module, List.of());
            allowedDependencies = allowedDependencies.or(DescribedPredicate.describe(
                    "명시적으로 공개한 동일 DB 트랜잭션·읽기 계약",
                    type -> contracts.stream().anyMatch(contract ->
                            type.getName().equals(ROOT + "." + contract)
                                    || type.getName().startsWith(ROOT + "." + contract + "$"))));
            classes().that().resideInAPackage(ROOT + "." + module + "..")
                    .should().onlyDependOnClassesThat(allowedDependencies)
                    .because("다른 모듈에는 명시적으로 검토한 공개 계약으로만 접근한다")
                    .check(PRODUCTION_CLASSES);
        }));
    }
}

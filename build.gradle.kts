plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "team4"
version = "0.0.1-SNAPSHOT"
description = "emotion-map backend"

java {
    // Java 21 (LTS) 로 toolchain 을 고정한다.
    // 팀원이 로컬에 어떤 JDK 를 깔았든 Gradle 이 21 을 찾아 사용/다운로드한다.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Web / REST ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Security + JWT ---
    // signup/login 만 공개(permitAll), 그 외 모든 엔드포인트는 인증 필요.
    // 비밀번호 해시는 security starter 의 BCryptPasswordEncoder 사용.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    testImplementation("org.springframework.security:spring-security-test")

    // --- API 문서 (OpenAPI 3 + Swagger UI) ---
    // 컨트롤러/DTO 에서 명세를 자동 생성. /swagger-ui.html 에서 확인·호출 테스트 가능.
    // springdoc 3.1.x 가 Spring Boot 4.x 대응 라인이다 (2.9.x 는 Spring Boot 3.x 용).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // --- Persistence (JPA + PostgreSQL + Flyway) ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4 부터 Flyway 자동구성은 spring-boot-flyway 모듈에 있다.
    // flyway-core 만 넣으면 자동구성이 동작하지 않아 마이그레이션이 실행되지 않는다(부팅 시 validate 실패).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- pgvector (JPA/Hibernate 용) ---
    // 감정 임베딩 벡터를 PostgreSQL 의 vector 타입으로 저장/검색한다.
    // JPA 엔티티에서 vector 를 매핑하려면 pgvector 공식 권장대로 hibernate-vector 를 쓴다.
    //   (JDBC 전용인 com.pgvector:pgvector 가 아니라 이 모듈. 버전은 Spring Boot BOM 이 관리.)
    // 엔티티 매핑 예: @JdbcTypeCode(SqlTypes.VECTOR) @Array(length = N) float[] embedding;
    implementation("org.hibernate.orm:hibernate-vector")

    // --- Ops / 관측 (헬스체크 등) ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- 개발 편의 ---
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- Lombok (생성자/Getter/Builder 등 보일러플레이트 생략) ---
    //   컴파일 시에만 필요. 런타임 의존성 아님.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // --- 테스트 (DB 비연결 단위테스트 전용) ---
    // Docker/Testcontainers 를 사용하지 않는다. DB 가 필요한 통합테스트는
    // 각자 로컬 PostgreSQL 로만 수동 수행하고, CI 에는 포함하지 않는다.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

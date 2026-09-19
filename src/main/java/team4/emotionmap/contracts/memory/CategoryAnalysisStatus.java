package team4.emotionmap.contracts.memory;

/**
 * {@code memories.category_analysis_status}: AI 실행 이력이며 최종 category 행의 존재·개수와 별개다.
 * API 분석 응답 categoryStatus 매핑(API_SPEC 4.1)은 CLASSIFIED→SUCCEEDED,
 * UNCLASSIFIED→INSUFFICIENT, FAILED→FAILED, 수동 저장→NOT_RUN이다.
 */
public enum CategoryAnalysisStatus { SUCCEEDED, INSUFFICIENT, FAILED, NOT_RUN }

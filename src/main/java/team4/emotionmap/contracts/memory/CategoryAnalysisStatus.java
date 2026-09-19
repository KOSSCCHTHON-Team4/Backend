package team4.emotionmap.contracts.memory;

/**
 * {@code memories.category_analysis_status}. API 분석 응답의 categoryStatus 매핑(API_SPEC 4.1):
 * CLASSIFIED→SUCCEEDED, UNCLASSIFIED→INSUFFICIENT, FAILED→FAILED, 수동 저장→NOT_RUN.
 */
public enum CategoryAnalysisStatus { SUCCEEDED, INSUFFICIENT, FAILED, NOT_RUN }

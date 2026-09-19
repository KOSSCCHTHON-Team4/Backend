-- Preserve the stored analysis-history claim using the names in API_SPEC 4.1 and ERD memories.
-- This is not derived from final category rows and does not certify a real AI provider invocation.
-- Flyway executes this migration transactionally; block concurrent memory changes during cutover.
LOCK TABLE memories IN ACCESS EXCLUSIVE MODE;

ALTER TABLE memories DROP CONSTRAINT memories_category_analysis_status_check;

UPDATE memories
SET category_analysis_status = CASE category_analysis_status
    WHEN 'CLASSIFIED' THEN 'SUCCEEDED'
    WHEN 'UNCLASSIFIED' THEN 'INSUFFICIENT'
    ELSE category_analysis_status
END
WHERE category_analysis_status IN ('CLASSIFIED', 'UNCLASSIFIED');

ALTER TABLE memories ADD CONSTRAINT memories_category_analysis_status_check
    CHECK (category_analysis_status IN ('SUCCEEDED', 'INSUFFICIENT', 'FAILED', 'NOT_RUN'));

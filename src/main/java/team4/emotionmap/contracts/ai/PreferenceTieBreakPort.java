package team4.emotionmap.contracts.ai;

/**
 * AI tie-break evaluation. The result's ordered rank groups preserve equal first-place candidates.
 * Consumers MUST validate a successful response against the request before using that group; final
 * random selection and fallback to the original highest-score group remain the consumer's responsibility.
 */
public interface PreferenceTieBreakPort {

    TieBreakResult rank(TieBreakRequest request);
}

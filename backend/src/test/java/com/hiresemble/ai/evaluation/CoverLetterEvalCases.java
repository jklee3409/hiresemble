package com.hiresemble.ai.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/** Synthetic golden cases for cover-letter quality evaluation. */
public final class CoverLetterEvalCases {

    public static final String RESOURCE = "/cover-letter-eval/cases.json";

    private CoverLetterEvalCases() {}

    public record Requirement(String category, String text, boolean required) {}

    public record Analysis(List<String> strengths, List<String> gaps, String summary) {
        public Analysis {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }
    }

    public record ResearchSource(
            String sourceType, String title, String snippet, String reliabilityNotice) {}

    public record CompanyResearch(String summary, List<ResearchSource> sources) {
        public CompanyResearch {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record Evidence(String category, String title, String content, String sourceExcerpt) {}

    public record EvalCase(
            String id,
            String label,
            String company,
            String jobTitle,
            String jobDescription,
            List<Requirement> requirements,
            String question,
            Integer maxLength,
            String memo,
            Analysis analysis,
            CompanyResearch companyResearch,
            List<Evidence> evidence) {
        public EvalCase {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record CaseFile(String schemaVersion, String notice, List<EvalCase> cases) {
        public CaseFile {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    public static List<EvalCase> load(ObjectMapper objectMapper) {
        try (InputStream input = CoverLetterEvalCases.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("evaluation cases are missing");
            }
            CaseFile file = objectMapper.readValue(input, CaseFile.class);
            if (!"cover-letter-eval-cases-v1".equals(file.schemaVersion()) || file.cases().isEmpty()) {
                throw new IllegalStateException("evaluation cases are invalid");
            }
            return file.cases();
        } catch (IOException exception) {
            throw new IllegalStateException("evaluation cases could not be read", exception);
        }
    }
}

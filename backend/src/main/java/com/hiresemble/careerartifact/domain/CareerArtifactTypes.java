package com.hiresemble.careerartifact.domain;

import com.hiresemble.agentrun.domain.model.WorkflowType;

public final class CareerArtifactTypes {

    public static final String RESOURCE_TYPE = "CAREER_ARTIFACT";
    public static final String RESUME_TEMPLATE_KEY = "resume-ats-v1";
    public static final String PORTFOLIO_TEMPLATE_KEY = "portfolio-interview-v1";
    public static final String TEMPLATE_VERSION = "1";
    public static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String PPTX_MIME =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private CareerArtifactTypes() {}

    public enum ArtifactType {
        RESUME,
        PORTFOLIO;

        public WorkflowType workflowType() {
            return this == RESUME
                    ? WorkflowType.RESUME_GENERATION
                    : WorkflowType.PORTFOLIO_GENERATION;
        }

        public String templateKey() {
            return this == RESUME ? RESUME_TEMPLATE_KEY : PORTFOLIO_TEMPLATE_KEY;
        }

        public String mimeType() {
            return this == RESUME ? DOCX_MIME : PPTX_MIME;
        }

        public String extension() {
            return this == RESUME ? "docx" : "pptx";
        }
    }

    public enum LifecycleStatus {
        ACTIVE,
        ARCHIVED
    }

    public enum GenerationStatus {
        NOT_STARTED,
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        INTERRUPTED
    }

    public enum ProfileSection {
        PROFILE,
        EDUCATIONS,
        CERTIFICATIONS,
        LANGUAGE_SCORES,
        AWARDS,
        CAREERS,
        ACTIVITIES
    }

    public enum EvidenceUsageType {
        PRIMARY_EXPERIENCE,
        STRENGTH,
        SUPPORTING_FACT
    }

    public enum PortfolioSlideType {
        COVER,
        PROFILE_SUMMARY,
        STRENGTH_OVERVIEW,
        PROJECT_CASE_STUDY,
        TECHNICAL_DECISION,
        IMPACT_AND_LEARNING,
        CLOSING
    }

    public enum PortfolioVisualType {
        NONE,
        PROCESS,
        ARCHITECTURE,
        TIMELINE,
        IMPACT_METRICS
    }
}

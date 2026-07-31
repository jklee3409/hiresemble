package com.hiresemble.interview.api;

import com.hiresemble.coverletter.api.CoverLetterDtos.JobRefDto;
import com.hiresemble.interview.api.InterviewDtos.AgentRunRefDto;
import com.hiresemble.interview.api.InterviewDtos.CoverLetterRefDto;
import com.hiresemble.interview.api.InterviewDtos.FeedbackScoreDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewAnswerVersionDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewFeedbackDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewQuestionDto;
import com.hiresemble.interview.api.InterviewDtos.QuestionSetDetailDto;
import com.hiresemble.interview.api.InterviewDtos.QuestionSetSummaryDto;
import com.hiresemble.interview.application.model.InterviewModels.AnswerVersionRow;
import com.hiresemble.interview.application.model.InterviewModels.EvidenceRefRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.QuestionSetRow;
import com.hiresemble.interview.application.model.InterviewModels.QuestionSetView;
import com.hiresemble.interview.application.model.InterviewModels.QuestionView;
import com.hiresemble.job.api.JobAnalysisDtos.EvidenceRefDto;
import com.hiresemble.research.api.ResearchApiMapper;
import org.springframework.stereotype.Component;

@Component
public class InterviewApiMapper {

    private final ResearchApiMapper researchMapper;

    public InterviewApiMapper(ResearchApiMapper researchMapper) {
        this.researchMapper = researchMapper;
    }

    public QuestionSetSummaryDto summary(QuestionSetRow value) {
        return new QuestionSetSummaryDto(
                value.id(),
                new JobRefDto(
                        value.jobId(),
                        value.companyName(),
                        value.positionName(),
                        value.jobTitle()),
                new CoverLetterRefDto(
                        value.coverLetterId(),
                        value.coverLetterTitle(),
                        value.coverLetterStatus()),
                value.title(),
                value.questionCount(),
                value.researchRunId(),
                value.sourceCoverage(),
                new AgentRunRefDto(
                        value.agentRunId(),
                        value.agentRunStatus(),
                        value.currentStep(),
                        value.progressPercent()),
                value.createdAt(),
                value.updatedAt());
    }

    public QuestionSetDetailDto detail(QuestionSetView value) {
        QuestionSetSummaryDto summary = summary(value.summary());
        return new QuestionSetDetailDto(
                summary.id(),
                summary.job(),
                summary.coverLetter(),
                summary.title(),
                summary.questionCount(),
                summary.researchRunId(),
                summary.sourceCoverage(),
                summary.agentRun(),
                summary.createdAt(),
                summary.updatedAt(),
                researchMapper.run(value.research()),
                value.questions().stream().map(this::question).toList());
    }

    public InterviewQuestionDto question(QuestionView value) {
        return new InterviewQuestionDto(
                value.question().id(),
                value.question().questionOrder(),
                value.question().questionType(),
                value.question().questionText(),
                value.question().intent(),
                value.question().evaluationPoints(),
                value.question().answerGuide(),
                value.question().followUpQuestions(),
                value.evidenceRefs().stream().map(this::evidence).toList(),
                value.sourceRefs().stream().map(researchMapper::sourceRef).toList(),
                value.question().sourceBased(),
                value.currentAnswer() == null ? null : answer(value.currentAnswer()),
                value.latestFeedback() == null ? null : feedback(value.latestFeedback()));
    }

    public InterviewAnswerVersionDto answer(AnswerVersionRow value) {
        return new InterviewAnswerVersionDto(
                value.id(),
                value.questionId(),
                value.parentVersionId(),
                value.versionNo(),
                value.content(),
                value.sourceType(),
                value.current(),
                value.createdAt());
    }

    public InterviewFeedbackDto feedback(FeedbackRow value) {
        return new InterviewFeedbackDto(
                value.id(),
                value.answerVersionId(),
                value.scores().stream()
                        .map(score -> new FeedbackScoreDto(
                                score.criterion(), score.score(), score.explanation()))
                        .toList(),
                value.strengths(),
                value.weaknesses(),
                value.suggestions(),
                value.revisedExample(),
                value.agentRunId(),
                value.createdAt());
    }

    private EvidenceRefDto evidence(EvidenceRefRow value) {
        return new EvidenceRefDto(
                value.id(),
                value.title(),
                value.category(),
                value.verificationStatus(),
                value.sourceType(),
                value.sourceDeleted());
    }
}

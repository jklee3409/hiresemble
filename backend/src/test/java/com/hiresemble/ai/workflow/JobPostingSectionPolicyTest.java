package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import org.junit.jupiter.api.Test;

class JobPostingSectionPolicyTest {

    private final JobPostingSectionPolicy policy = new JobPostingSectionPolicy();

    @Test
    void plateerFdePostingKeepsRoleIntroductionOutOfSevenScorableSourceBullets() {
        var blocks = policy.segment("""
                플래티어의 AX개발자(FDE)란?
                고객사의 복잡한 비즈니스 영토 최전선에 서서 AX Foundry를 이식하는 역할입니다.
                기업 맞춤형 지능형 신경망을 완벽하게 이식합니다.
                주요 업무
                유통·금융·공공·제조 고객의 레거시 인프라 파악 및 맞춤형 AI 연동 구조 설계
                현장 실무 지식이 반영된 Ontology 매핑 및 최적화된 RAG 모듈 적용
                안전한 온프레미스/프라이빗 독립형 AI 실행 환경 구축 및 최적화
                MCP 기반 이종 AI 시스템 연동·확장과 솔루션 간 통합 환경 구축
                지원 자격
                컴퓨터공학 및 인프라 기본기: 다양한 운영체제 환경을 이해하고 RESTful API 설계 및 웹 아키텍처에 대한 이해가 탄탄하신 분
                데이터베이스 및 RAG 관심도: SQL 및 NoSQL 활용이 원활하며 벡터 및 그래프 DB 프로젝트나 학습을 진행해 보신 분
                비즈니스 커뮤니케이션: 기술 내용을 비개발자도 이해하기 쉽게 설명하고 복잡한 요구사항을 논리적 규격으로 설계하시는 분
                """);

        assertThat(blocks).filteredOn(block -> block.section() == RequirementSection.ROLE_SUMMARY)
                .hasSize(2);
        assertThat(blocks).filteredOn(block -> block.section() == RequirementSection.RESPONSIBILITY)
                .hasSize(4);
        assertThat(blocks)
                .filteredOn(block -> block.section() == RequirementSection.REQUIRED_QUALIFICATION)
                .hasSize(3);
        assertThat(blocks)
                .noneMatch(block -> block.sourceText().contains("플래티어의 AX개발자"));
    }
}

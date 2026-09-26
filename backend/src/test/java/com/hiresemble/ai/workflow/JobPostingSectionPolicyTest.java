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

    @Test
    void multiRolePostingKeepsProcessBenefitsNoticesAndContactOutOfQualifications() {
        var blocks = policy.segment("""
                NH투자증권 2026년 하반기 대졸 신입사원 채용
                채용 구분: 공채
                접수 마감: 2026-09-28 17:00
                모집직무 및 우대역량(요약):
                - PB: 개인, 법인 고객 대상 자산관리 서비스 등
                ※ 모집지역: 수도권, 대구·경북권, 광주·전라권
                우대역량: 금융시장 이해도 및 원활한 대고객 커뮤니케이션 능력
                - IT: 증권시스템 전산 개발·운영
                우대역량: 전산 관련 전공, 프로그래밍 언어 및 SQL 활용 능력, 개발 프로젝트 또는 시스템 운영 경험
                모집인원:
                - 00명
                지원자격:
                - 국내/외 대학 학사 이상 기졸업자 및 2027년 2월 내 졸업예정자
                - 해외여행에 결격사유가 없는 자
                - 남성의 경우 병역필 또는 면제자
                - 2027년 1월 입사하여 정상근무 가능한 자
                채용절차 및 일정:
                서류전형
                ① 지원서 접수: 9.18(금) - 9.28(월) 17:00
                입사: '27년 1월 입사
                복리후생(요약):
                - 업계 최고 급여: 신입사원 초봉 6,400만원 이상
                - 경조사 지원: 본인 및 가족 경조금, 휴가, 물품 제공
                유의사항:
                - 청탁 등 부정행위 확인 시 합격취소 및 향후 5년간 응시 제한
                문의:
                채용홈페이지 (https://nhqv.recruiter.co.kr) 채용QnA 문의하기 이용
                """);

        assertThat(blocks)
                .filteredOn(block -> block.section() == RequirementSection.REQUIRED_QUALIFICATION)
                .extracting(block -> block.sourceText())
                .containsExactly(
                        "국내/외 대학 학사 이상 기졸업자 및 2027년 2월 내 졸업예정자",
                        "해외여행에 결격사유가 없는 자",
                        "남성의 경우 병역필 또는 면제자",
                        "2027년 1월 입사하여 정상근무 가능한 자");
        assertThat(blocks)
                .filteredOn(block -> block.section() == RequirementSection.PREFERRED_QUALIFICATION)
                .extracting(block -> block.sourceText())
                .containsExactly(
                        "금융시장 이해도 및 원활한 대고객 커뮤니케이션 능력",
                        "전산 관련 전공, 프로그래밍 언어 및 SQL 활용 능력, 개발 프로젝트 또는 시스템 운영 경험");
        assertThat(blocks)
                .filteredOn(block -> block.section() == RequirementSection.RESPONSIBILITY)
                .extracting(block -> block.sourceText())
                .containsExactly("PB: 개인, 법인 고객 대상 자산관리 서비스 등", "IT: 증권시스템 전산 개발·운영");
        assertThat(blocks)
                .filteredOn(block -> block.section() != RequirementSection.OTHER)
                .noneMatch(block -> block.sourceText().contains("지원서 접수")
                        || block.sourceText().contains("초봉")
                        || block.sourceText().contains("부정행위")
                        || block.sourceText().contains("채용QnA")
                        || block.sourceText().contains("모집지역")
                        || block.sourceText().contains("수도권")
                        || block.sourceText().contains("00명"));
    }

    @Test
    void enumeratedDutyEndingWithDeungStaysOneCriterionWhileSkillListsSplit() {
        var blocks = policy.segment("""
                모집직무
                결제업무: 국내외 주식, 채권 및 장외파생 상품 매매, 결제, 정산, 권리행사 등
                우대역량: 외국어 능력(영어), 재무·회계지식
                """);
        var sources = blocks.stream()
                .map(block -> new JobAnalysisWorkflow.ProviderSourceRequirement(
                        block.sourceBlockId(), block.sourceText(), block.sourceOrdinal()))
                .toList();

        assertThat(new JobRequirementNormalizationPolicy().normalize(sources, blocks))
                .extracting(criterion -> criterion.section() + "|" + criterion.text())
                .containsExactly(
                        "RESPONSIBILITY|결제업무: 국내외 주식, 채권 및 장외파생 상품 매매, 결제, 정산, 권리행사 등",
                        "PREFERRED_QUALIFICATION|외국어 능력(영어)",
                        "PREFERRED_QUALIFICATION|재무·회계지식");
    }

    @Test
    void mergedRoleLinesSplitBySentenceAndDropLocationAndEmploymentTypeNotes() {
        var blocks = policy.segment("""
                모집직무 및 우대역량 (모집직무 간 중복지원 불가)
                - PB: 개인, 법인 고객 대상 자산관리 서비스 등. 모집지역: 수도권, 대구·경북권, 광주·전라권. 금융시장 이해도 및 원활한 대고객 커뮤니케이션 능력.
                - 글로벌사업: 글로벌사업 전략 수립 및 실행, 해외거점 관리 등. 비즈니스 영어 구사 능력, 글로벌 금융시장 이해도. (정규직)
                - IT: 증권시스템 전산 개발·운영. 전산 관련 전공, 프로그래밍 언어 및 SQL 활용 능력, 3.5년 이상 운영 경험.
                """);
        var sources = blocks.stream()
                .map(block -> new JobAnalysisWorkflow.ProviderSourceRequirement(
                        block.sourceBlockId(), block.sourceText(), block.sourceOrdinal()))
                .toList();

        assertThat(new JobRequirementNormalizationPolicy().normalize(sources, blocks))
                .extracting(criterion -> criterion.text())
                .containsExactly(
                        "PB: 개인, 법인 고객 대상 자산관리 서비스 등",
                        "금융시장 이해도 및 원활한 대고객 커뮤니케이션 능력",
                        "글로벌사업: 글로벌사업 전략 수립 및 실행, 해외거점 관리 등",
                        "비즈니스 영어 구사 능력",
                        "글로벌 금융시장 이해도",
                        "IT: 증권시스템 전산 개발·운영",
                        "전산 관련 전공",
                        "프로그래밍 언어 및 SQL 활용 능력",
                        "3.5년 이상 운영 경험");
    }

    @Test
    void conditionAndDutyLinesStartingWithHeadingWordsStayInTheirSection() {
        var blocks = policy.segment("""
                자격요건
                근무지 이동 가능자
                복지 분야 실무 경험 3년 이상
                주요 업무
                급여 정산 업무
                지원자격: 학사 이상
                """);

        assertThat(blocks).extracting(block -> block.section() + "|" + block.sourceText())
                .containsExactly(
                        "REQUIRED_QUALIFICATION|근무지 이동 가능자",
                        "REQUIRED_QUALIFICATION|복지 분야 실무 경험 3년 이상",
                        "RESPONSIBILITY|급여 정산 업무",
                        "REQUIRED_QUALIFICATION|학사 이상");
    }
}

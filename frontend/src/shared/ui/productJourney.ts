export const PRODUCT_JOURNEY_STEPS = [
  {
    number: 1,
    icon: 'profile',
    title: '내 정보와 경험 정리',
    description: '기본 정보와 경력·활동을 적고, 분석에 사용할 경험을 직접 확인해요.',
  },
  {
    number: 2,
    icon: 'documents',
    title: '이력서와 포트폴리오 등록',
    description: '파일을 올리면 내용을 읽고, 이후 지원 준비에 참고할 경험을 정리해요.',
  },
  {
    number: 3,
    icon: 'jobs',
    title: '관심 공고 자동 분석',
    description: '공고를 등록하면 본문 확인부터 내 경험과의 비교까지 자동으로 이어져요.',
  },
  {
    number: 4,
    icon: 'cover-letter',
    title: '자기소개서 준비',
    description: '공고 분석과 확인한 경험을 바탕으로 질문별 초안을 만들고 직접 다듬어요.',
  },
  {
    number: 5,
    icon: 'interview',
    title: '면접 질문과 피드백',
    description: '공고와 자기소개서를 바탕으로 예상 질문을 확인하고 답변 피드백을 준비해요.',
  },
] as const

export type ProductJourneyStep = (typeof PRODUCT_JOURNEY_STEPS)[number]

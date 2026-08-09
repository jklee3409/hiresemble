import type {
  EducationLevel,
  EducationStatus,
  EmploymentDisqualificationStatus,
  MilitaryStatus,
  OverseasTravelEligibility,
} from '@/shared/api/contracts'
import type { AppSelectOption } from '@/shared/ui/AppSelect.vue'

/*
 * 온보딩과 기본 정보 화면이 같은 서버 enum을 같은 문구로 보여 주도록 한곳에서 관리한다.
 * 화면마다 문구가 달라지면 사용자가 같은 값을 다른 선택지로 오해한다.
 */

export const MILITARY_STATUS_OPTIONS: AppSelectOption<MilitaryStatus>[] = [
  { value: 'UNSPECIFIED', label: '선택하지 않음' },
  { value: 'COMPLETED', label: '이행' },
  { value: 'EXEMPT', label: '면제' },
  { value: 'NOT_APPLICABLE', label: '해당 없음' },
  { value: 'NOT_COMPLETED', label: '미이행' },
]

export const OVERSEAS_TRAVEL_OPTIONS: AppSelectOption<OverseasTravelEligibility>[] = [
  { value: 'UNSPECIFIED', label: '선택하지 않음' },
  { value: 'ELIGIBLE', label: '가능' },
  { value: 'RESTRICTED', label: '제한 있음' },
]

export const EMPLOYMENT_DISQUALIFICATION_OPTIONS: AppSelectOption<EmploymentDisqualificationStatus>[] =
  [
    { value: 'UNSPECIFIED', label: '선택하지 않음' },
    { value: 'NONE_DECLARED', label: '없음' },
    { value: 'HAS_RESTRICTION', label: '제한 있음' },
  ]

export const EDUCATION_LEVEL_OPTIONS: AppSelectOption<EducationLevel>[] = [
  { value: 'HIGH_SCHOOL', label: '고등학교' },
  { value: 'ASSOCIATE', label: '대학교(전문학사)' },
  { value: 'BACHELOR', label: '대학교(학사)' },
  { value: 'MASTER', label: '대학원(석사)' },
  { value: 'DOCTORATE', label: '대학원(박사)' },
  { value: 'OTHER', label: '기타 교육' },
]

export const EDUCATION_STATUS_OPTIONS: AppSelectOption<EducationStatus>[] = [
  { value: 'ENROLLED', label: '재학' },
  { value: 'LEAVE_OF_ABSENCE', label: '휴학' },
  { value: 'EXPECTED_GRADUATION', label: '졸업 예정' },
  { value: 'GRADUATED', label: '졸업' },
  { value: 'WITHDRAWN', label: '중퇴' },
]

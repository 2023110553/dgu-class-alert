# ClassAlert

동국대학교 Moodle 기반 eClass의 과제 마감과 온라인 강의 학습 종료 일정을 ICS 캘린더에서 가져와 기기에 저장하고 알림을 예약하는 **비공식 Android 앱**입니다. 학교의 공식 앱이 아니며 eClass 접근 제한을 우회하지 않습니다.

## 주요 기능

- 개인 eClass 캘린더 내보내기 URL 등록(토큰 포함 URL은 Android Keystore AES-GCM 암호화 저장)
- 앱 실행 시 동기화 및 WorkManager 기반 24시간 주기 백그라운드 동기화
- Room 기반 로컬 일정 보관, UID 중복 방지, 변경된 마감 알림 재예약
- 과제 및 `Progress stop` 온라인 강의 종료 일정 구분
- 기본 3일/1일/3시간 전 AlarmManager 알림, 사용자 시간 설정 및 종류별 켜기/끄기
- Android 13+ 알림 권한, 정확한 알람 불가 시 비정확 알람 대체, 재부팅 후 알림 복구
- 홈/전체 일정/설정 Compose UI와 개별 일정 숨기기, 테스트 알림

## 개발 환경과 빌드

- Android Studio에서 이 폴더를 열고 JDK 17, Android SDK 36을 설치합니다.
- `local.properties`에 `sdk.dir`을 설정한 뒤 `gradle testDebugUnitTest assembleDebug`를 실행합니다.
- 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
- GitHub Actions도 동일한 단위 테스트와 APK 빌드를 수행합니다.

## 사용 방법

1. eClass 캘린더 내보내기 화면에서 본인의 URL을 복사합니다. URL은 `https://eclass.dongguk.edu/calendar/export_execute.php`로 시작해야 합니다.
2. ClassAlert 설정에서 URL을 저장하고 알림 권한을 허용합니다.
3. 저장 후 동기화 결과와 앞으로 2주 일정을 홈에서 확인합니다.

## 보안과 한계

- 실제 토큰과 실제 ICS 데이터는 저장소와 로그에 포함하지 않습니다.
- ICS만으로 제출·수강 완료 여부를 알 수 없어 완료 상태를 추측하거나 자동으로 알림을 취소하지 않습니다.
- `Progress stop`은 “온라인 강의 학습 종료 예정”으로만 표현하며 결석/지각 확정으로 해석하지 않습니다.
- 캘린더 응답에 없는 기존 일정을 자동 삭제하지 않아 월 경계나 내보내기 범위 변경으로 인한 오삭제를 막습니다.
- WorkManager 실행 시각과 정확한 알람은 OS 배터리 정책, 권한, 제조사 강제 종료 정책에 따라 지연될 수 있습니다.
- 공지사항 크롤링, 제출/수강 완료 자동 감지, 실제 eClass 토큰 통합 테스트는 1차 버전에 포함하지 않았습니다.

## 테스트 범위

`IcsParserTest`는 UTC 시각, 월말/월초, folded line, 이스케이프, 누락 UID, 과제/강의 시작/강의 종료 분류를 검증합니다. Room upsert와 UID 기본키가 중복 저장을 방지하며 저장소는 변경된 시각을 감지해 기존 알림을 취소한 뒤 재예약합니다. 재부팅 복구와 권한 거부는 Android 계측 및 실기기 검증 대상입니다.

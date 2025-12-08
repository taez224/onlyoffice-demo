

## 들어가며

**ONLYOFFICE**는 간단히 말하면 오픈소스 기반 문서 편집 솔루션이다. 라트비아의 **Ascensio System SIA**에서 개발했으며, 한국에서는 **Wesome**이 공식 Gold Partner로 기술지원, 컨설팅, 구매대행 등을 맡고 있다.

> ONLYOFFICE 공식: https://www.onlyoffice.com
> Wesome의 ONLYOFFICE 소개: https://www.wesome.co.kr/onlyoffice/
>
<p align="center" style="color:gray">
  <img style="margin:50px 0 10px 0" src="https://velog.velcdn.com/images/taez224/post/7adae199-15ca-4250-93c7-12c851b9fe66/image.png" alt="wesome onlyoffice" width=800 />
  Wesome ONLYOFFICE 주요 실적. 출처: Wesome
</p> 

Wesome 페이지의 _주요 실적_ 중 한 곳에 참여했었는데, 보듯이 2024년부터 국내 기업들에 도입되기 시작한 것 같다.

ONLYOFFICE 자체가 대부분 보안이 엄격한 환경에 도입되는 특성상 공개된 레퍼런스 자체가 많지 않았고, LLM 활용도 당연히 불가했다. 정작 물어봐도 당시 LLM은 ONLYOFFICE 구조를 잘 몰랐기 때문에 ONLYOFFICE 공식 문서와 Github, 포럼을 뒤지고 메일로 직접 문의를 넣어가며 개발했던 기억이 생생하다.

이번 시리즈는 그 시행착오를 기억할 겸, `ONLYOFFICE Docs Integration Server` 데모 프로토타입을 만들어보며 개념과 흐름을 정리해보려고 한다. 당연히 실제 프로젝트 코드는 가져올 수 없으니, 이번 기회에 핫하다는 **AntiGravity**로 빠르게 만들어보고, 하나씩 디벨롭하는 방식으로 진행 할 예정이다.


---

## ONLYOFFICE란?

> 참고: OnlyOffice Basic Concepts
https://api.onlyoffice.com/docs/docs-api/get-started/basic-concepts/
> <p align="center" style="color:gray">
  <img style="margin:50px 0 10px 0" src="https://velog.velcdn.com/images/taez224/post/734eb332-1066-43a6-bc5d-7b1427b02816/image.png" alt="onlyoffice products" width=800 />
  ONLYOFFICE 제품군. 출처: Wesome
</p> 


이번 시리즈의 주인공인 **ONLYOFFICE Docs**는 온프레미스 환경에 설치할 수 있는 **웹 기반 오피스 스위트**다. 브라우저를 통해 Word/Excel/PPT 등 문서를 편집할 수 있고, 실시간 공동 작업도 지원한다.
특히 어느 한국 기업에서 요청을 했는지 **HWP/HWPX(한글 문서)**도 지원하기 시작했다.

### 핵심 기능
- **문서 보기 및 편집** (DOCX, XLSX, PPTX, PDF 등)
- **실시간 공동 작업**
- **버전 관리 및 변경 추적**
- **문서 포맷 변환** (HWP → DOCX, DOCX → PDF 등)

여기까지 보면 Google Docs, Office 365와 비슷해 보이지만, **보안 규제가 강한 기업 환경(금융, 제조, 공공 분야 등)**에서는 클라우드 기반 SaaS 도입이 어려우므로 ONLYOFFICE 같은 온프레미스 솔루션이 요구된다.

### 온프레미스 환경이 필요한 경우
- 내부망(망분리)에서 운영해야 함
- 문서가 외부 클라우드로 유출되면 안됨
- 사내 스토리지 연동
- 파일 접근·권한 정책을 기존 사내 시스템과 완전히 통합해야 함
- 로그, 보안 정책, 감사(Audit) 항목을 사내 기준으로 커스터마이징해야 함

이런 조건을 충족하면서 웹 기반 공동 편집까지 지원하는 솔루션은 현재 **ONLYOFFICE**가 거의 유일한 현실적인 선택지라 할 수 있다.

즉, ONLYOFFICE는 단순히 에디터를 제공하는 것을 넘어,
**“기업 내부 시스템에 맞춘 문서 협업 환경을 자체 구축할 수 있게 해주는 플랫폼”**
이라고 볼 수 있겠다.

---


## ONLYOFFICE 아키텍처

ONLYOFFICE를 처음 접하면 _“문서 편집기 하나 붙이는 작업”_ 처럼 생각할 수 있다. (나처럼..)
하지만 실제로는 기업 내부의 보안 정책과 협업 요구를 모두 만족시키는 **작은 문서 플랫폼**을 구축하는 일에 가깝다.
그리고 내부 구조를 이해하지 못하면 연동 과정에서 상당한 시행착오를 겪게 될 것이다.

공식 문서 기준, ONLYOFFICE는 기능별로 다음과 같은 모듈로 나뉜다.


### ONLYOFFICE Docs가 제공하는 모듈

#### 1) Document Editor (클라이언트 에디터 UI)

- 브라우저에 `iframe`으로 embed되는 실제 에디터 컴포넌트
- Word/Excel/PPT/PDF 편집 UI 제공
- 실시간 공동 편집과 변경 트래킹 등을 시각화
- 내부적으로 `Document Editing Service`와 `WebSocket`으로 통신

여러 형태로 제공되지만, 이 시리즈에서는 React 라이브러리를 사용할 예정

#### 2) Document Editing Service

- **Editor 뒤에서 모든 편집 연산을 처리하는 서버 모듈**
- 문서 로딩, diff/merge, change tracking, 실시간 sync 등 처리
- `Document Editor`와 `WebSocket`으로 통신

간단히 말해 `Editor`는 UI, `Editing Service`는 그 뒤에서 돌아가는 엔진 역할이다.

#### 3) Document Command Service

- 서버에서 에디터로 **특정 명령을 전달**할 때 사용하는 모듈
    - 강제 저장(force save)
    - 문서 리로드
    - 편집 세션 관리 등..
- **관리자 기능, 자동화, 백그라운드 문서 처리 등에서 사용**

#### 4) Document Conversion Service

- 문서 포맷 변환 담당
    - DOCX → PDF
    - PPTX → PDF
    - HWP/HWPX → DOCX 등..
- 편집 가능한 `Office Open XML` 형식으로 표준화
- **문서 변환 수요가 많은 기업 환경에서 자주 사용하게 되는 모듈**


#### 4) Document Builder Service

- 에디터를 띄우지 않고 서버에서 문서 생성
- 대량 문서 생성, 템플릿 병합, 자동 보고서 생성 등에 활용
- **문서 자동화 시스템 구축 시 필요**


---

### 개발자가 직접 구현해야 하는 부분

**ONLYOFFICE Docs**는 원샷 솔루션이 아니다.
기존 시스템과의 연동이 목적이기에 ONLYOFFICE와 우리 시스템의 **역할 경계와 책임을 잘 구분**해야 한다.
위의 `ONLYOFFICE Docs가 제공하는 모듈`에 포함되지 않은, _문서 목록·권한·업/다운로드·저장·버전관리_ 등은 **연계하려는 서버에서 직접 제공**해야 한다.

그러므로, 다음 두 가지 컴포넌트를 개발자가 필수적으로 구현해야 한다. _- 우리의 실습 대상이다. -_

#### 1) Document Manager (Client side)
- 사용자가 보는 문서 목록 UI
- 문서를 선택하고 `열기/보기/편집` 옵션을 제공하는 화면
- 사용자 인증 및 권한에 따른 편집 기능 제어
- 에디터에 설정 값(JWT) 주입
- UI까지 제공하는 Workspace 제품을 쓰지 않는 이상 **개발자가 직접 구성해야 하는 영역**

실제 서비스와 연계에서는 **연동 시스템의 인증/인가 체계와 호환**되게 하는 것이 중요한 이슈이지만, 이번 시리즈에서는 그 부분은 생략하고 React로 간단히 CRUD 수준의 구현 예정 _- 추후 여유가 생기면 진행해볼수도..? -_

#### 2) Document Storage Service (Server side)

**이 부분이 ONLYOFFICE 연동의 핵심이자, 많은 개발자들이 헤매는 영역이다.**


> 공식 문서의 어딘가...
*"Document storage service … must be implemented by the software integrators."*

ONLYOFFICE는 **문서를 직접 저장하지 않는다.** 작업 내용을 cache로 가지고 있다가
작업이 끝나면 **callback**으로 ___'이 파일을 저장하라'___ 는 시그널만 보내고,
실제 파일 저장·버전관리·권한 검증 등은 **우리의 Document Storage Service가 담당한다.**

**Document Storage Service**가 수행해야 하는 역할은 다음과 같다:

- 사용자 권한 체크
- 문서 ID(FileKey) 관리
- 파일 다운로드/업로드 API 제공
- 이력 및 버전 관리
- 스토리지 선택(S3/MinIO/NAS 등)
- 에디터 Config JWT 발급
- ONLYOFFICE에서 오는 `callback` 처리

**결국 이 서비스가 전체 ONLYOFFICE 연계의 코어이다.**

---


### ONLYOFFICE 모듈 별 역할 요약
| 모듈 | 역할 | 비고 |
|---|---|---|
| **Document Manager** | 문서 목록 UI, Editor 렌더링 | React로 구현 예정 |
| **Document Storage Service ** | 인증/인가, Config JWT 생성, 파일 관리, Callback 처리 | Spring Boot로 구현 예정 |
| **Document Editing Service** | 문서 편집 엔진, 실시간 협업 관리 | ONLYOFFICE Docs에 포함 |
| **Document Command Service** | 강제 저장, 세션 관리 등 명령 제어 | ONLYOFFICE Docs에 포함 |
| **Document Conversion Service** | 파일 형식 변환 (HWP→DOCX, DOCX→PDF 등) | ONLYOFFICE Docs에 포함 |
| **File Storage** | 실제 파일 저장소 | 로컬 테스트용 File Storage, S3 Mocking 등으로 구현 예정 |


---


## 다음 편에서는

2편부터 실제 예제 레포에서 아래 내용을 하나씩 구현해보며 데모 버전을 만들어본다.

- **Docker**로 `ONLYOFFICE Docs Server` 구동
    - JWT 설정 등 옵션 정의

- **Spring Boot**로 `Document Storage Service` 골격 만들기
    - `ONLYOFFICE Docs`와 연결되는 Config JWT 생성
    - 파일 업로드/다운로드 API
    - `callback` 처리
    - storage는 `localFS` 부터 시작

- **React**로 간단한 `Document Manager UI` 구성
    - `Document Storage Service`와 연결 테스트


예제 레포는 철저한 **Vibe Coding**으로 진행될 예정이니, 코드 퀄리티보다는 전체 흐름과 개념 등 **바이브에 중점을** 뒀으면 한다.

---

### 데모 버전의 다이어그램

#### ONLYOFFICE 문서 편집 flowchart

![](https://velog.velcdn.com/images/taez224/post/e056e73b-582a-49f6-8fc5-28a139bc80c0/image.png)



#### 문서 편집 과정 요청/응답 시퀀스 다이어그램:

![](https://velog.velcdn.com/images/taez224/post/bbeb153a-8740-4249-9db9-ddbbdbd81cb5/image.png)

---

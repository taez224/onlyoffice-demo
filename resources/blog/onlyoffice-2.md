

> ## "코딩은 기세(Vibe)다."
> _- Gemini 3 Pro_

개발자라면 누구나 한 번쯤 겪어봤을 것이다. 새로운 기술을 도입하려고 문서를 폈는데, 방대한 양에 압도되어 "아, 이걸 언제 다 읽고 구현하지?" 하며 한숨 쉬었던 순간을.

이번 ONLYOFFICE 연동 프로젝트는 달랐다. 나에게는 **Antigravity**라는 강력한 AI 페어 프로그래머가 있었기 때문이다.

이 글은 복잡하기로 소문난 ONLYOFFICE 문서 편집기를 **단 몇 분 만에** 연동해낸, 그 짜릿했던 **Vibe Coding**의 기록이다.

---

## Step 1: "계획부터 세워봐" (Architecture)

성늩 확인 겸 일부러 최소한의 프롬프트로 진행했다.

> **User**: "ONLYOFFICE 연동 데모 프로젝트 만들어줘. 백엔드는 java 21, spring boot 3 기반. 프론트엔드는 typescript, react 기반. onlyoffice document server는 지금 내가 도커에 띄워놓은걸 사용할거야."

내 요청이 끝나기가 무섭게, Antigravity는 이미 전체 구현 계획을 화면에 띄우고 있었다.

> **Antigravity**: "ONLYOFFICE는 백엔드와의 통신이 핵심입니다. React 프론트엔드, Spring Boot 백엔드, 그리고 Docker 컨테이너 간의 통신 흐름을 이렇게 설계했습니다. 바로 구현 들어갈까요?"


Antigravity는 단 한 번만에 완벽한 청사진을 그려냈다.



나는 그저 멍하니 화면을 바라보다 한마디 했을 뿐이다.
**"완벽해. 진행시켜."**

---

## Step 2: "알아서 피해가는 Docker의 늪" (Networking)

구현이 시작되자마자 나는 내심 걱정했다. '아, 이거 Docker 컨테이너 통신 설정 때문에 분명 삽질 좀 하겠네.'

하지만 코드를 열어본 순간 내 걱정은 기우였음을 깨달았다. `application.yml` 파일에는 이미 정답이 적혀 있었다.

```yaml
# application.yml
server:
  baseUrl: http://host.docker.internal:8080  # Antigravity가 이미 설정함
```

내가 "Docker 설정은..." 이라고 입을 떼기도 전에, Antigravity는 이미 `host.docker.internal`을 적용해 두었던 것이다. 덕분에 첫 번째 함정 `Connection refused` 에러는 구경조차 할 수 없었다.

---

## Step 3: "보안은 기본 옵션입니다" (JWT Security)

기능이 돌아가는 걸 보고 "이제 보안을 신경 써야겠지?"라고 생각하며 코드를 다시 들여다봤다. 그런데 놀랍게도, **이미 모든 보안 설정이 끝나 있었다.\***
_* 사실 안끝나있었다._

```java
// EditorController.java
// Antigravity가 처음부터 작성한 코드:
// 전체 설정을 서명하여 위변조를 원천 봉쇄한다.
String token = jwtManager.createToken(config);
config.put("token", token);
```

심지어 `docker-compose.yml`의 `JWT_SECRET`과 백엔드의 `secret` 키까지 완벽하게 동기화되어 있었다.

> **User**: "잠깐, 보안 설정까지 다 해놓은 거야?"
> **Antigravity**: "물론이죠. 프로덕션 레벨의 연동\*을 원하셨으니까요. JWT 없이는 배포할 수 없습니다."
> _*사실 프로덕션 레벨의 연동을 원한 적은 없다._

나는 그저 고개를 끄덕이며 실행 버튼만 누르면 됐다.

---

## Step 4: "저장 로직? 이미 완료되었습니다" (Callback Handling)

"그래도 저장 로직은 복잡하니까 내가 좀 봐줘야겠지?"
ONLYOFFICE의 Callback 처리는 까다롭기로 유명하다. 비동기 요청, 상태 코드 파싱, 파일 다운로드...

하지만 `CallbackController`를 열어본 나는 헛웃음을 지을 수밖에 없었다.

```java
// CallbackController.java
// status 2(저장)와 6(강제 저장)을 정확히 캐치해서 처리
if (status == 2 || status == 6) {
    String downloadUrl = (String) callbackData.get("url");
    documentService.saveFileFromUrl(downloadUrl, fileName);
}
```

완벽했다. 테스트 파일에 "Hello Vibe Coding!"을 입력하고 저장 버튼을 눌렀다. 1초 뒤, 내 로컬 폴더의 파일이 정확하게 갱신되었다.

**어떤 지시도, 수정도 필요 없었다. 그냥 처음부터 완벽하게 동작했다.**

---

## 마치며: AI가 운전하는 슈퍼카에 탄 기분

이 모든 과정을 혼자서 했다면? 공식 문서를 읽고, 아키텍처를 고민하고, Docker 네트워크 에러와 싸우고, JWT 토큰 오류를 디버깅하느라 며칠 밤을 새웠을 것이다.

하지만 Antigravity와 함께한 이번 프로젝트는 내가 운전대를 잡을 필요조차 없었다. 나는 그저 조수석에 앉아 창밖 풍경을 즐기며, 가끔 **"좋아, 계속 가!"**라고 외치기만 하면 됐다.

- **아키텍처?** 알아서 설계됨.
- **Docker 네트워킹?** 알아서 해결됨.
- **보안?** 알아서 적용됨.
- **비즈니스 로직?** 알아서 구현됨.

이것이 바로 우리가 추구해야 할 **Vibe Coding**의 정점이 아닐까? 내가 고민하기도 전에 답을 내놓는 파트너와 함께하는 것.

**"자, 다음 Vibe 타러 가볼까?"**

---


............


## 여기까지는 Gemini가 자기가 만든 프로젝트로 자화자찬하는 글을 쓴 것이다.

생각보다 한번에 돌아는 갈 정도로는 나와서서 내심 놀라긴 했다..
물론 상세 구현은 전부 뜯어고쳐야하지만 어쨌든 데모로는 적당하다.


이제부터는 실제 ONLYOFFICE 연동 흐름을 코드를 따라 정리해가며
왜 이 코드가 이렇게 동작하는지를 인간의 관점에서 파헤쳐보겠다.

> 예제 레포: https://github.com/taez224/onlyoffice-demo

---


## 연동 구조 (Architecture by Gemini)

전체적인 흐름은 아래 다이어그램으로 정리할 수 있다.

![archi](https://velog.velcdn.com/images/taez224/post/9da7f0cc-69eb-4e51-baac-21cb95e6e3a2/image.png)


새로운 프로젝트를 탐색할 때 여러 방식이 있겠지만 개인적으로는 도메인, 엔티티 모델을 시작으로 타고간다. 이 데모에는 아직 그런건 없으므로 API 부터 알아보자.

### ONLYOFFICE 연동 핵심 API

| API | 방향 | 시점 | 내용 |
|---|---|---|---|
| 1. Editor Config 요청 | 프론트엔드 → 백엔드 | 프론트엔드가 에디터 초기화 전에 백엔드에 에디터 설정을 요청<br/>`GET /api/config?fileName=..`  | 백엔드가 JWT를 생성하고, 응답에 `token`이 포함 된 `config` 객체와 `documentServerUrl` 리턴 |
| 2. 에디터 초기화 | 프론트 → ONLYOFFICE | `DocumentEditor` 컴포넌트에 `config` 와 `documentServerUrl`을 props로 제공 | ONLYOFFICE는 `JWT_ENABLED=true`시 config에 포함된 token 검증 |
| 3. 파일 다운로드 | ONLYOFFICE → 백엔드 | ONLYOFFICE 서버에서 편집 대상 문서 다운로드<br/>`GET /files/{fileName}` | ONLYOFFICE는 앞서 받은 `token`의 `payload`에서 `document.url`  획득 후 요청 |
| 4. Callback | ONLYOFFICE → 백엔드 | 사용자가 에디터 편집 후 종료 (또는 force-save)<br/>`POST /callback` | ONLYOFFICE는 앞서 받은 `token`을 헤더 혹은 바디에 담아 전송. 백엔드에서는 JWT 검증 및 payload에 따라 진행 |

여기서 `2. 에디터 초기화`는 `ONLYOFFICE DOCS`에 내장되어 있는 API며,
나머지 3개가 우리의 Gemini가 백엔드에 만들어 준 API이다.
다이어그램과 위 설명으로 이해가 되셨으면 좋겠지만 한 번 더 살펴보면..


### 주요 설정 값

#### 백엔드: application.yml
Gemini가 만들어준 기본 `application.yml` 파일이다.

```yaml
# Server configuration (base URL for backend)
# In Docker environment use host.docker.internal; for local dev you can set http://localhost:8080
server:
  baseUrl: http://host.docker.internal:8080

# ONLYOFFICE Document Server Settings
# Replace with your actual Document Server URL
onlyoffice:
  url: http://localhost:9980
  # The secret key must match the one in ONLYOFFICE Document Server config (local.json or env var)
  secret: your-secret-key-must-be-at-least-32-characters-long-for-hs256

# Storage configuration
storage:
  path: storage
```

- `server.baseUrl`: ONLYOFFICE에서 이 백엔드에 접근 시 사용하는 url이다. ONLYOFFICE는 docker 컨테이너 내부이고, 이 백엔드는 호스트 머신을 사용하므로 `host.docker.internal` 도메인 적용
- `onlyoffice`
    - `url`: ONLYOFFICE 서버의 url
    - `secret`: 백엔드와 ONLYOFFICE에서 JWT 서명/검증시 사용할 값

#### ONLYOFFICE: docker-compose.yml

```yaml
services:
  onlyoffice-docs:
    image: onlyoffice/documentserver:9.1
    container_name: onlyoffice-docs
    ports:
      - "9980:80" # sync with backend's onlyoffice.url
    environment:
      # To enable JWT, set JWT_ENABLED=true and provide a secret
      - JWT_ENABLED=true
      - JWT_SECRET=your-secret-key-must-be-at-least-32-characters-long-for-hs256
      # - JWT_HEADER=Authorization # 기본값
      # - JWT_IN_BODY=true
    volumes:
      - ./onlyoffice_data/logs:/var/log/onlyoffice
      - ./onlyoffice_data/data:/var/www/onlyoffice/Data
      - ./onlyoffice_data/lib:/var/lib/onlyoffice
```

- `application.yml`의 `onlyoffice.url`,`onlyoffice.secret` 값과 sync 확인
- ONLYOFFICE는 `JWT_ENABLED=true` 일 때 `config`에 포함된 토큰 검증.
    - 토큰이 유효하지 않으면 **_“Document security token is not correctly formed”_** 오류 발생.


### 1. Editor Config 요청

> ONLYOFFICE 공식 문서: https://api.onlyoffice.com/docs/docs-api/get-started/how-it-works/opening-file/

ONLYOFFICE 에디터로 문서를 열기 위한 config 등의 정보를 얻기 위한 API


```java
// EditorController.java
@GetMapping("/api/config")
public Map<String, Object> getEditorConfig(@RequestParam String fileName) {
    File file = documentService.getFile(fileName);
    String serverUrl = documentService.getServerUrl(); // "http://host.docker.internal:8080"
    Map<String, Object> config = new HashMap<>();
    
    // … document, editorConfig 객체 구성 …
    Map<String, Object> document = new HashMap<>();
    document.put("title", fileName);
    document.put("url", serverUrl + "/files/" + fileName);
    document.put("key", fileName + "_" + file.lastModified()); // 💩
   
    config.put("document", document);
    
    Map<String, Object> editorConfig = new HashMap<>();
    editorConfig.put("callbackUrl", serverUrl + "/callback?fileName=" + fileName);
    
    config.put("editorConfig", editorConfig);

    // 전체 config 를 JWT 로 서명
    String token = jwtManager.createToken(config);
    config.put("token", token);

    Map<String, Object> response = new HashMap<>();
    response.put("config", config);
    response.put("documentServerUrl", onlyofficeUrl); // "http://localhost:9980"
    return response;
}
```

Vibe Coding이다보니 태클 걸고싶은 부분이 한두군데가 아니지만 우선 흐름만 짚어보면
- 응답에는 `documentServerUrl`과 `config`가 팔요
    - `documentServerUrl`: ONLYOFFICE 서버 URL - `http://localhost:9980`
    - `config`: DocumentEditor에 제공할 config 객체
        - `editorConfig`: `callbackUrl`과 사용자 정보 등
        - `document`: 문서 정보 - **`downloadUrl`과 `key`**
        - `JWT_ENABLED=true` 시 `config` 객체 암호화 필요

`serverUrl`과 `onlyofficeUrl`이 헷갈릴 수 있는데 다시 정리하면
- `onlyofficeUrl`:** ONLYOFFICE 서버의 URL.** `사용자의 브라우저(Frontend)에서` ONLYOFFICE 에디터 스크립트를 로드할 때 사용한다. (`http://localhost:9980`)
- `serverUrl`: **이 Spring boot 서버의 URL.** `ONLYOFFICE 서버에서` 파일 다운로드 요청이나 콜백을 보낼 때 사용한다. (`http://host.docker.internal:8080`)


### 2. 에디터 초기화

```tsx
import React, { useEffect, useState } from 'react';
import { DocumentEditor } from '@onlyoffice/document-editor-react';
// 생략...

    return (
        <div style={{ height: '100vh', width: '100%' }}>
            <DocumentEditor
                id="docxEditor"
                documentServerUrl={config.documentServerUrl}
                config={config.config}
                events_onDocumentReady={() => console.log("Document Ready")}
            />
        </div>
    );
};

```

역시 코드 훈수는 잠시 접어두고 DocumentEditor의 내부 동작을 알아보자.
1. `1. Editor Config 요청`을 통해 얻은 `documentServerUrl`과 `config`를 `DocumentEditor`의 props로 넘겨주면
2. `DocumentEditor`는 `documentServerUrl`을 통해 기본적인 Editor 관련 스크립트를 받고
- WebSocket으로 `DocumentEditor`와 `Document Editing Service`간 통신
3. `config`의 token 검증 후
4. token에 포함 된 `document.url`을 통해 문서를 내려받는다.
5. 특정 상황마다 `editorConfig.callbackUrl`로 콜백 전송




### 3. 파일 다운로드 from ONLYOFFICE
위에서 ONLYOFFICE Editor token 검증 후 token에 포함된 `document.url` 을 통해 **ONLYOFFICE 서버에서** 해당 파일을 다운로드한다.

> `document.url` 예시: http://host.docker.internal:8080/files/sample.docx

```java

@GetMapping("/files/{fileName}")
public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
    File file = documentService.getFile(fileName);
    if (!file.exists()) {
        return ResponseEntity.notFound().build();
    }

    Resource resource = new FileSystemResource(file);

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
}
```

뭐 별거없으니 넘어가자. 물론 실제로는 여기에서도 각종 검증 절차가 필요하지만 그건 다음 페이즈에서 진행하고 우선 **이 API는 ONLYOFFICE 서버에서 호출한다**는 것만 이해하자.
그러므로 Docker 내부의 ONLYOFFICE가 host의 백엔드에 접근하기 위해 `host.docker.internal` 처리.




### 4. Callback URL

> ONLYOFFICE 공식: [Callback Handler](https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/)

**ONLYOFFICE 연계의 핵심**이라 할 수 있는 콜백 핸들러 부분이다. 공식 홈페이지에서 가이드를 꼼꼼히 읽는 걸 추천한다. 여기서는 역시 흐름 위주로 간단히 설명한다.

callback url은 특정 상황마다 ONLYOFFICE 서버에서 status값을 담아 호출하게 되는데 아래를 참고하자.
![callback status](https://velog.velcdn.com/images/taez224/post/3a077d7b-95b8-4ad0-95ae-a24e191ec626/image.png)

현재 우리의 서비스에 구현된 부분은 저장 관련 콜백이다.

이전 글에 설명했듯이, ONLYOFFICE는 문서를 실제로 저장하지 않는다. 대신 편집 종료 시 callback URL로 “지금 이 파일 저장하면 됨”라고 알려준다.

- **`2`**: 일반 저장(에디터 통해 편집 후 에디터 종료)
- **`6`**: `강제 저장(force save)` 요청 시


```java
// CallbackController.java
@PostMapping("/callback")
public Map<String, Object> callback(HttpServletRequest request, @RequestBody String body) {
    
    // ① Authorization 헤더에서 토큰 추출 및 검증
    String authHeader = request.getHeader("Authorization");
    if (!jwtManager.validateToken(authHeader)) {
        return Map.of("error", 1); // 비정상 시 응답
    }

    // ② status 가 2 혹은 6 일 때 파일 저장
    Integer status = (Integer) body.get("status");
    if (status != null && (status == 2 || status == 6)) {
        // 파일 다운로드 & 저장 로직
        String downloadUrl = (String) body.get("url");
        String key = (String) body.get("key");
        String fileName = request.getParameter("fileName");
        
        try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            documentService.saveFile(fileName, in);
        } catch (Exception e) {
            return Map.of("error", 1); // 비정상 시 응답
        }
    }

    return Map.of("error", 0); // ONLYOFFICE가 기대하는 정상 응답
}
```

Callback의 핵심은
- ONLYOFFICE 서버에서 특정 상황 시 `callback url` 호출
    - `JWT_SECRET` 으로 암호화된 JWT 형태로 `callback` 데이터 전송
    - `{ "error": 0 }` 응답하면 ONLYOFFICE에서 정상으로 간주 - 이외의 응답은 오류로 간주
    - 저장 관련 콜백일 경우 callback 데이터에 해당 문서의 `downloadUrl` 을 통해 해당 문서 실제 저장 처리 등 후속 프로세스 핸들링



---

## AntiGravity 간단 후기

AntiGravity - Gemini 3로 간단한 ONLYOFFICE 연동 구현은 생각 이상으로 빠르게 됐다.

그런데 자랑으로 내세웠던 AntiGravity의 크롬 브라우저 기반 프론트엔드 디버깅엔 함정이 있었는데
iframe으로 띄워지는 ONLYOFFICE 에디터가 문제였는지, 대놓고 에디터가 오류 창을 띄우고 있는데도 `"완벽하게 문서가 열렸습니다!"` 라고 뻥카를...

다음 글에서부터 본격적인 `Document Storage Service` 구현에 들어간다.
단순 데모를 넘어 실제 문서를 Object Storage를 통해 관리하고 메타 데이터 등을 활용하는 방법 등을 적용한다.

AntiGravity 체험은 이정도로하고 다시 Claude Code로 돌아가야지..


# 신뢰저장소에 넣는 중간 인증서

## 왜 여기 있나

메일 서버(`mail.dshw.co.kr`)가 TLS 인사 때 **자기 인증서 하나만 보내고 중간 CA 를 안 보낸다**
(2026-09-28 실측 — `openssl s_client -showcerts` 결과 인증서 1장).

- 루트(`Sectigo Public Server Authentication Root R46`)는 Java 기본 신뢰저장소에 **있다**.
- 빠진 것은 그 사이의 중간 CA(`... CA OV R36`) 하나뿐이다.
- Windows(.NET)는 이 조각을 알아서 받아오기 때문에 레거시 프로그램은 아무 문제가 없었고,
  Java 만 `PKIX path building failed` 로 막혔다.

## 왜 AIA 옵션이 아니라 이 방법인가

처음엔 `-Dcom.sun.security.enableAIAcaIssuers=true`(JVM 이 중간 인증서를 직접 받아오게 하는 옵션)로
해결하려 했으나 **실제로 돌려 보니 안 됐다**. 같은 조건에서:

```
AIA 끔  → PKIX path building failed
AIA 켬  → PKIX path building failed   (동일)
중간 인증서 주입 → OK (TLSv1.2, 호스트명 검증 포함)
```

그래서 확인된 방법으로 간다. 옵션 한 줄이 더 깔끔해 보였지만 통하지 않았다.

## 왜 인증서 검증을 끄지 않는가

`mail.smtp.ssl.trust=<host>` 로 그 호스트의 검증을 건너뛸 수도 있다(설정은 남겨 뒀다).
하지만 그러면 중간자가 가짜 인증서를 내밀어도 통과하고, **레거시(.NET)보다 약해진다**.
여기서는 빠진 조각만 채워 정상 검증을 끝낸다.

## 갱신

이 파일은 **중간 CA** 인증서다(서버 인증서가 아니다). 서버 인증서를 갱신해도 보통 그대로 쓴다.
중간 CA 가 바뀌면 그때 교체한다 — 받는 곳:

```
curl -o inter.crt http://crt.sectigo.com/SectigoPublicServerAuthenticationCAOVR36.crt
openssl x509 -inform DER -in inter.crt -out sectigo-public-server-auth-ca-ov-r36.pem
```

## 근본 해결

**메일 서버가 중간 인증서를 함께 보내도록 고치는 것**이 정답이다(서버 설정 한 줄).
그렇게 되면 이 파일과 Dockerfile 의 주입 단계를 지우면 된다.
공개 인증서라 저장소에 두어도 문제없다 — 비밀정보가 아니다.

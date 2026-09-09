# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# --- run stage ---
FROM eclipse-temurin:21-jre

# ★타임존을 한국으로 고정한다.
#   컨테이너 기본이 UTC라 LocalDate.now() 가 오전 9시 이전에는 **전날**을 돌려준다.
#   이 값이 매출취소 역분개 날짜·마감(period_lock) 판정·대시보드 기본기간에 그대로 쓰여,
#   아침에 취소 한 건 넣으면 전날 장부가 움직이고 그 달이 마감돼 있으면 거부된다.
#   로그 시각이 CloudWatch에서 9시간 어긋나 보이던 것도 같은 원인이다.
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# ‼️TZ 환경변수만으로는 OS의 tz 파일 유무에 기댄다. JVM 옵션으로 한 번 더 못박아
#   어느 베이스 이미지에서든 같은 날짜가 나오게 한다(LocalDate.now() 가 장부 날짜가 된다).
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]

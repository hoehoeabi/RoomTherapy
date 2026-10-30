# 1. 빌드 스테이지
# 빌드용 JDK 17 이미지. 빌드 끝나면 버릴 거라 builder로 네이밍
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build

# gradlew랑 설정 파일들 먼저 복사
# 소스코드 바뀌어도 라이브러리 안 바뀌었으면 캐시 써서 빌드 속도 올리려고 분리해둠
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스코드 복사해서 jar 파일 빌드 (테스트는 깃액션에서 도니까 여기선 -x test로 스킵)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon


# 2. 실행 스테이지
# 실제 배포용. 빌드 도구 다 빼고 JRE만 써서 이미지 용량 줄임
FROM eclipse-temurin:17-jre-jammy AS runner
WORKDIR /app

# 서버 로그랑 DB 시간 맞추려고 한국 표준시(KST)로 설정
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# root 권한으로 돌리면 보안 위험하니까 spring 유저 따로 파서 실행
RUN groupadd -r spring && useradd -r -g spring spring

# 업로드 폴더 미리 만들어두고 권한 부여
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# 빌드 스테이지에서 나온 jar 파일만 챙겨오기
COPY --from=builder --chown=spring:spring /build/build/libs/*.jar app.jar

USER spring
EXPOSE 8080

# 도커 메모리 제한(RAM) 인식하게 하고 OOM 방지용으로 75% 설정
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

# prod 프로필 기본으로 실행되게 해둠. 로컬 테스트할 땐 뒤에 옵션 덮어씌우면 됨
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar ${0} ${@}"]
CMD ["--spring.profiles.active=prod"]

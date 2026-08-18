FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar -x test

FROM eclipse-temurin:21-jre
# 컨테이너 자체도 한국 시각으로 — 로그 시각이 애플리케이션과 어긋나지 않게 한다.
# (애플리케이션 기준 시각은 BackendApplication에서 따로 고정한다)
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]
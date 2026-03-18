FROM gradle:8.5.0-jdk21-jammy AS build

WORKDIR /app

COPY . .

# 개행문자 오류 해결 [unix와 window 시스템 차이]
RUN sed -i 's/\r$//' gradlew


RUN chmod +x ./gradlew
RUN ./gradlew bootJar

#이미지 굽기
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

#build라고 별명 지은 gradle 이미지를 빌드하면 /app/build/libs/*.jar 위치에 생성되는데 해당 jar파일을 /app로 가져와서 app.jar로 rename한다.
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

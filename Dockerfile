# Imagem da API. O Render constrói a partir daqui.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY api/pom.xml pom.xml
COPY api/src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 quizlab
WORKDIR /app
COPY --from=build /app/target/quizlab-api-1.0-SNAPSHOT.jar app.jar
USER quizlab

# O plano gratuito do Render tem 512 MB de memória: limita a JVM para caber.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

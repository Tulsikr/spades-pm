
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY . .

RUN mvn -B -DskipTests compile

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app /app

EXPOSE 8080

CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "target/classes:$CLASSPATH", "Main"]

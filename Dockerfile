FROM openjdk:8u342-jre
VOLUME /tmp
COPY *.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

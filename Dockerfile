FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN find src/main/java -name "*.java" > sources.txt && javac -d bin @sources.txt
EXPOSE 8080
CMD ["java", "-cp", "bin", "com.underground.parking.UndergroundParkingServer"]

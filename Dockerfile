FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac -d bin src/main/java/com/underground/parking/*.java
EXPOSE 8080
CMD ["java", "-cp", "bin", "com.underground.parking.Main"]

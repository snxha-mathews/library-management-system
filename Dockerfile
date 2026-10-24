FROM eclipse-temurin:21
WORKDIR /app
COPY . /app
RUN javac LibraryManagementSystem.java
CMD ["java", "LibraryManagementSystem"]
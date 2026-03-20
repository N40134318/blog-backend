FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /workspace

# 复制项目
COPY . .

# 给 mvnw 权限
RUN chmod +x mvnw

# 运行 Spring Boot
CMD ["./mvnw", "spring-boot:run"]
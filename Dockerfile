# =========================================================================
# ESTÁGIO 1: Build da aplicação com Maven (local)
# =========================================================================
# Este estágio é executado localmente, não no Docker
# O JAR já foi compilado localmente com: mvn clean package -DskipTests -s settings.xml

# =========================================================================
# ESTÁGIO 2: Criação da imagem final otimizada
# =========================================================================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Cria um usuário não-root para segurança
RUN apt-get update && apt-get install -y wget curl && rm -rf /var/lib/apt/lists/*
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copia o JAR compilado localmente
COPY target/ms-user-consents-1.0.0.jar app.jar

# Define as permissões corretas
RUN chown -R appuser:appuser /app
USER appuser

# Expõe as portas necessárias
EXPOSE 8086

# Configurações JVM otimizadas para container
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport"

# Comando de inicialização
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]


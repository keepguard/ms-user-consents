#!/bin/bash

set -e

SERVICE_NAME="ms-user-consents"
POM_FILE="/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/backend/ms/${SERVICE_NAME}/pom.xml"
DOCKER_COMPOSE_FILE="/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/docker/infra/api/docker-compose.yml"
DOCKERFILE_PATH="/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/backend/ms/${SERVICE_NAME}/Dockerfile"
TARGET_DIR="/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/backend/ms/${SERVICE_NAME}/target"

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Função para logging
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Função para extrair versão do POM
get_current_version() {
    grep -A 5 "<artifactId>${SERVICE_NAME}</artifactId>" "$POM_FILE" | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | xargs
}

# Função para incrementar versão (patch +1)
increment_version() {
    local version=$1
    local major=$(echo $version | cut -d. -f1)
    local minor=$(echo $version | cut -d. -f2)
    local patch=$(echo $version | cut -d. -f3 | cut -d- -f1)
    local suffix=$(echo $version | cut -d- -f2-)
    
    patch=$((patch + 1))
    echo "${major}.${minor}.${patch}-${suffix}"
}

# Função para atualizar versão no POM
update_pom_version() {
    local new_version=$1
    log_info "Atualizando POM para versão: $new_version"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "/<artifactId>${SERVICE_NAME}<\/artifactId>/,/<version>/s|<version>.*</version>|<version>$new_version</version>|" "$POM_FILE"
    else
        sed -i "/<artifactId>${SERVICE_NAME}<\/artifactId>/,/<version>/s|<version>.*</version>|<version>$new_version</version>|" "$POM_FILE"
    fi
    
    log_success "POM atualizado para: $new_version"
}

# Verificar parâmetros
DEPLOY_DOCKER=false
if [ "$1" = "up" ]; then
    DEPLOY_DOCKER=true
    log_info "Modo: Build + Push + Deploy Docker"
else
    log_info "Modo: Build + Push (GitHub apenas)"
fi

# Extrai versão do pom.xml
VERSION=$(get_current_version)

if [ -z "$VERSION" ]; then
    log_error "Não foi possível extrair a versão do pom.xml"
    exit 1
fi

log_info "Versão detectada: ${VERSION}"

# Define imagens
REGISTRY="ghcr.io/keepguard"
IMAGE_NAME="${REGISTRY}/${SERVICE_NAME}"
IMAGE_TAG="${IMAGE_NAME}:${VERSION}"
IMAGE_LATEST="${IMAGE_NAME}:latest"

log_info "============================================"
log_info "  Deploy ${SERVICE_NAME}"
log_info "============================================"
log_info "Registry: ${REGISTRY}"
log_info "Image: ${IMAGE_TAG}"
log_info "============================================"

# 1. Build Maven
log_info "Executando Maven clean package..."
cd "/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/backend/ms/${SERVICE_NAME}"
mvn clean package -DskipTests

if [ ! -f "${TARGET_DIR}/${SERVICE_NAME}-${VERSION}.jar" ]; then
    log_error "JAR não encontrado: ${TARGET_DIR}/${SERVICE_NAME}-${VERSION}.jar"
    exit 1
fi

log_success "Build Maven concluído com sucesso"

# 2. Prepara Dockerfile
log_info "Preparando Dockerfile..."
cp "${DOCKERFILE_PATH}" "${DOCKERFILE_PATH}.bak"
sed "s/VERSION_PLACEHOLDER/${VERSION}/g" "${DOCKERFILE_PATH}.bak" > "${DOCKERFILE_PATH}"
log_success "Dockerfile preparado"

# 3. Build Docker Image
log_info "Construindo imagem Docker..."
docker build -t "${IMAGE_TAG}" -t "${IMAGE_LATEST}" .

if [ $? -ne 0 ]; then
    log_error "Falha ao construir imagem Docker"
    mv "${DOCKERFILE_PATH}.bak" "${DOCKERFILE_PATH}"
    exit 1
fi

log_success "Imagem Docker construída: ${IMAGE_TAG}"

# 4. Push para GitHub Container Registry
log_info "Fazendo push para GitHub Container Registry..."
docker push "${IMAGE_TAG}"
docker push "${IMAGE_LATEST}"

if [ $? -ne 0 ]; then
    log_error "Falha ao fazer push da imagem"
    mv "${DOCKERFILE_PATH}.bak" "${DOCKERFILE_PATH}"
    exit 1
fi

log_success "Push concluído com sucesso"

# 5. Restaura Dockerfile
mv "${DOCKERFILE_PATH}.bak" "${DOCKERFILE_PATH}"

# 6. Atualiza docker-compose.yml
log_info "Atualizando docker-compose.yml..."
sed -i '' "s|image: ${REGISTRY}/${SERVICE_NAME}:.*|image: ${IMAGE_TAG}|g" "${DOCKER_COMPOSE_FILE}"
log_success "docker-compose.yml atualizado"

# 7. Deploy no Docker Compose (se parâmetro "up")
if [ "$DEPLOY_DOCKER" = true ]; then
    log_info "Fazendo deploy no Docker Compose..."
    cd "/Users/rafaelnogueirasoares/Projetos/keepguard/keepguard-core/docker/infra/api"
    docker-compose up -d ${SERVICE_NAME}
    
    if [ $? -eq 0 ]; then
        log_success "Container ${SERVICE_NAME} iniciado com sucesso"
    else
        log_warning "Falha ao iniciar container ${SERVICE_NAME}"
    fi
fi

# 8. Auto-incrementa versão do POM
log_info "Incrementando versão do POM..."
NEXT_VERSION=$(increment_version "$VERSION")
update_pom_version "$NEXT_VERSION"

log_success "============================================"
log_success "  Deploy concluído com sucesso!"
log_success "============================================"
log_info "Imagem: ${IMAGE_TAG}"
log_info "Latest: ${IMAGE_LATEST}"
log_info "Próxima versão: ${NEXT_VERSION}"
log_success "============================================"
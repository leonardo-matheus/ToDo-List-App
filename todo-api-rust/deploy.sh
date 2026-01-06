#!/bin/bash
# =====================================================
# Script de Deploy - Todo API Rust
# Execute na VPS Ubuntu: bash deploy.sh
# =====================================================

set -e

echo "🚀 Iniciando deploy da Todo API Rust..."

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Verificar se está rodando como root
if [ "$EUID" -eq 0 ]; then
    echo -e "${RED}❌ Não execute como root! Use um usuário normal com sudo.${NC}"
    exit 1
fi

# Diretório do projeto
PROJECT_DIR="/opt/todo-api"
SERVICE_NAME="todo-api"

echo -e "${YELLOW}📦 Instalando dependências do sistema...${NC}"
sudo apt update
sudo apt install -y build-essential pkg-config libssl-dev default-libmysqlclient-dev curl git

# Instalar Rust se não existir
if ! command -v cargo &> /dev/null; then
    echo -e "${YELLOW}🦀 Instalando Rust...${NC}"
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
    source $HOME/.cargo/env
else
    echo -e "${GREEN}✅ Rust já está instalado${NC}"
fi

# Verificar versão do Rust
rustc --version
cargo --version

# Criar diretório se não existir
if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${YELLOW}📁 Criando diretório do projeto...${NC}"
    sudo mkdir -p $PROJECT_DIR
    sudo chown -R $USER:$USER $PROJECT_DIR
fi

# Verificar se já tem o código
if [ ! -f "$PROJECT_DIR/Cargo.toml" ]; then
    echo -e "${RED}❌ Código não encontrado em $PROJECT_DIR${NC}"
    echo "Copie os arquivos do projeto para $PROJECT_DIR e execute novamente"
    exit 1
fi

cd $PROJECT_DIR

# Verificar .env
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo -e "${YELLOW}⚙️ Criando arquivo .env a partir do exemplo...${NC}"
        cp .env.example .env
        echo -e "${RED}⚠️ IMPORTANTE: Edite o arquivo .env com suas configurações!${NC}"
        echo "Execute: nano $PROJECT_DIR/.env"
        exit 1
    else
        echo -e "${RED}❌ Arquivo .env não encontrado!${NC}"
        exit 1
    fi
fi

echo -e "${YELLOW}🔨 Compilando para produção...${NC}"
cargo build --release

echo -e "${YELLOW}🔧 Configurando serviço systemd...${NC}"

# Criar arquivo de serviço
sudo tee /etc/systemd/system/$SERVICE_NAME.service > /dev/null <<EOF
[Unit]
Description=Todo App API (Rust)
After=network.target mysql.service

[Service]
Type=simple
User=$USER
Group=$USER
WorkingDirectory=$PROJECT_DIR
ExecStart=$PROJECT_DIR/target/release/todo-api
Restart=always
RestartSec=5
Environment=RUST_LOG=info

[Install]
WantedBy=multi-user.target
EOF

echo -e "${YELLOW}🔄 Recarregando systemd...${NC}"
sudo systemctl daemon-reload

echo -e "${YELLOW}🟢 Iniciando serviço...${NC}"
sudo systemctl enable $SERVICE_NAME
sudo systemctl restart $SERVICE_NAME

# Aguardar inicialização
sleep 3

# Verificar status
if sudo systemctl is-active --quiet $SERVICE_NAME; then
    echo -e "${GREEN}✅ Serviço iniciado com sucesso!${NC}"
    sudo systemctl status $SERVICE_NAME --no-pager
else
    echo -e "${RED}❌ Falha ao iniciar o serviço${NC}"
    sudo journalctl -u $SERVICE_NAME -n 50 --no-pager
    exit 1
fi

# Testar API
echo -e "${YELLOW}🧪 Testando API...${NC}"
sleep 2
if curl -s http://localhost:8080 | grep -q "success"; then
    echo -e "${GREEN}✅ API respondendo corretamente!${NC}"
else
    echo -e "${RED}⚠️ API pode não estar respondendo. Verifique os logs:${NC}"
    echo "sudo journalctl -u $SERVICE_NAME -f"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 Deploy concluído!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "📡 API rodando em: http://localhost:8080"
echo ""
echo "📝 Comandos úteis:"
echo "   Ver logs:    sudo journalctl -u $SERVICE_NAME -f"
echo "   Reiniciar:   sudo systemctl restart $SERVICE_NAME"
echo "   Parar:       sudo systemctl stop $SERVICE_NAME"
echo "   Status:      sudo systemctl status $SERVICE_NAME"
echo ""
echo "🔐 Próximo passo: Configure o Nginx como proxy reverso"
echo "   Ver README.md para instruções detalhadas"

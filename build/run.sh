#!/bin/bash

# Default values
TIME_LORD="false"
SERVER_PORT="8080"
DEDICATED_STORAGE="100000000" # 100MB in bytes

# Parse optional arguments
for arg in "$@"; do
  case $arg in
    --timelord=*)
      TIME_LORD="${arg#*=}"
      shift
      ;;
    --server.port=*)
      SERVER_PORT="${arg#*=}"
      shift
      ;;
    --dedicatedStorage=*)
      DEDICATED_STORAGE="${arg#*=}"
      shift
      ;;
  esac
done

# Detect OS type
OS_TYPE=$(uname)
echo "📦 OS detected: $OS_TYPE"

# Detect local IP address based on OS
if [ "$OS_TYPE" = "Darwin" ]; then
  # macOS
  LOCAL_IP=$(ipconfig getifaddr en0)
elif [ "$OS_TYPE" = "Linux" ]; then
  # Linux
  LOCAL_IP=$(hostname -I | awk '{print $1}')
else
  echo "❌ Unsupported OS: $OS_TYPE"
  exit 1
fi

if [ -z "$LOCAL_IP" ]; then
  echo "❌ Could not detect local IP address."
  exit 1
fi

# Check for upnpc
if ! command -v upnpc >/dev/null 2>&1; then
  echo "❌ UPnP tool 'upnpc' not found."
  
  if [ "$OS_TYPE" = "Darwin" ]; then
    echo "👉 Installing miniupnpc via Homebrew..."
    # Install on macOS
    brew install miniupnpc
  elif [ "$OS_TYPE" = "Linux" ]; then
    echo "👉 Installing miniupnpc using APT..."
    # Install on Linux
    sudo apt update && sudo apt install -y miniupnpc
  fi

  # Check if installation was successful
  if ! command -v upnpc >/dev/null 2>&1; then
    echo "❌ Failed to install miniupnpc. Exiting..."
    exit 1
  fi
fi

<<com
# Check if port is already forwarded
if upnpc -l 2>/dev/null | grep -q "$SERVER_PORT.*$LOCAL_IP:$SERVER_PORT"; then
  echo "ℹ️ Port $SERVER_PORT is already forwarded to $LOCAL_IP:$SERVER_PORT"
else
  echo "🔁 Forwarding port $SERVER_PORT to $LOCAL_IP:$SERVER_PORT via UPnP..."
  upnpc -e "ArchiveMint Node - Port $SERVER_PORT" -a "$LOCAL_IP" "$SERVER_PORT" "$SERVER_PORT" TCP
fi
com
echo "here"
# Fetch public IP address
FARMER_IP=$(curl -4 ifconfig.me 2>/dev/null | tr -d '%')
LOCAL_NODE="http://$FARMER_IP:$SERVER_PORT"

# Create temporary config file
cat > ./applicationSH.yaml <<EOF
spring:
  threads:
    virtual:
      enabled: true
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
server:
  tomcat:
    max-connections: 5000
    accept-count: 2000
    max-threads: 2000
    connection-timeout: 60000
  servlet:
      session-timeout: 30m
app:
  storagePath: ./nodes/\${app.id}
  filesToPlotPath: ./nodes/files
  farmerAddress: "http://$FARMER_IP"
  seedNodes:
    - "$LOCAL_NODE"
    - "http://85.245.113.27:8080"
    - "http://85.245.113.27:8082"
  fccnPublicKey: "3059301306072a8648ce3d020106082a8648ce3d03010703420004e4a88d98f5ece99dae11bd4309307557e24876c2d389932c7b83f4c6012c41c803638b9aa38257f33d0a4137c9371fd59c13536ff807fa80261c8596d717d493"
  fccnNetworkAddress: "http://85.245.113.27:8085"
  dedicatedStorage: $DEDICATED_STORAGE
  initializeStorage: true
  initialVDFIterations: 3000000
  timelord: $TIME_LORD
EOF

echo "🚀 Launching ArchiveMint..."
echo "  TIME_LORD=$TIME_LORD"
echo "  SERVER_PORT=$SERVER_PORT"
echo "  LOCAL_IP=$LOCAL_IP"
echo "  FARMER_IP=$FARMER_IP"
echo "  LOCAL_NODE=$LOCAL_NODE"

java -jar ./ArchiveMint.jar --spring.config.location=file:./applicationSH.yaml --app.id=$SERVER_PORT --server.port=$SERVER_PORT

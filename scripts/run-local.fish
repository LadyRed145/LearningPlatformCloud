#!/usr/bin/env fish

cd (dirname (status filename))/..

echo "===== DETENIENDO DOCKER PARA EVITAR PUERTOS OCUPADOS ====="
docker compose down --remove-orphans 2>/dev/null

echo "===== CARGANDO VARIABLES LOCAL ====="

set -gx DB_URL "jdbc:oracle:thin:@semana1_high?TNS_ADMIN=/home/natalia/.oracle/wallet_semana1"
set -gx DB_USERNAME (string replace "DB_USERNAME=" "" (grep "^DB_USERNAME=" .env))
set -gx DB_PASSWORD (string replace "DB_PASSWORD=" "" (grep "^DB_PASSWORD=" .env))

set -gx AWS_REGION (string replace "AWS_REGION=" "" (grep "^AWS_REGION=" .env))
set -gx AWS_S3_BUCKET_NAME (string replace "AWS_S3_BUCKET_NAME=" "" (grep "^AWS_S3_BUCKET_NAME=" .env))

set -gx AZURE_B2C_CLIENT_ID (string replace "AZURE_B2C_CLIENT_ID=" "" (grep "^AZURE_B2C_CLIENT_ID=" .env))
set -gx AZURE_B2C_ISSUER_URI (string replace "AZURE_B2C_ISSUER_URI=" "" (grep "^AZURE_B2C_ISSUER_URI=" .env))
set -gx AZURE_B2C_JWK_SET_URI (string replace "AZURE_B2C_JWK_SET_URI=" "" (grep "^AZURE_B2C_JWK_SET_URI=" .env))

set -gx RABBITMQ_HOST "localhost"
set -gx RABBITMQ_PORT "5672"
set -gx RABBITMQ_USERNAME (string replace "RABBITMQ_USERNAME=" "" (grep "^RABBITMQ_USERNAME=" .env))
set -gx RABBITMQ_PASSWORD (string replace "RABBITMQ_PASSWORD=" "" (grep "^RABBITMQ_PASSWORD=" .env))
set -gx RABBITMQ_QUEUE (string replace "RABBITMQ_QUEUE=" "" (grep "^RABBITMQ_QUEUE=" .env))
set -gx RABBITMQ_EXCHANGE (string replace "RABBITMQ_EXCHANGE=" "" (grep "^RABBITMQ_EXCHANGE=" .env))
set -gx RABBITMQ_ROUTING_KEY (string replace "RABBITMQ_ROUTING_KEY=" "" (grep "^RABBITMQ_ROUTING_KEY=" .env))

echo "DB_URL cargado:" (test -n "$DB_URL"; and echo "SI"; or echo "NO")
echo "DB_USERNAME cargado:" (test -n "$DB_USERNAME"; and echo "SI"; or echo "NO")
echo "AZURE cargado:" (test -n "$AZURE_B2C_CLIENT_ID"; and echo "SI"; or echo "NO")

echo "===== LEVANTANDO SOLO RABBITMQ LOCAL ====="
docker compose up -d rabbitmq

sleep 12

echo "===== EJECUTANDO BACKEND LOCAL ====="
./mvnw spring-boot:run

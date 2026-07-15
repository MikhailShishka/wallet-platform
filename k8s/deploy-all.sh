#!/bin/bash
# ============================================================
# deploy-all.sh — деплой всей wallet-platform в Kubernetes.
#
# Требования:
# 1. kubectl настроен и подключён к кластеру.
# 2. Docker-образы всех 4 сервисов доступны кластеру:
#    - Для minikube: `eval $(minikube docker-env)` и docker build локально.
#    - Для облачного K8s: push в registry, imagePullPolicy: Always с полным путём.
# 3. Ingress Controller установлен (для minikube: `minikube addons enable ingress`).
# ============================================================

set -e

K8S_DIR="$(dirname "$0")"

echo "=== Deploying wallet-platform to Kubernetes ==="
echo ""

echo "[1/4] Creating namespace..."
kubectl apply -f "$K8S_DIR/00-namespace.yaml"

echo ""
echo "[2/4] Deploying infrastructure (Postgres, Mongo, Redis, Kafka, Jaeger)..."
kubectl apply -f "$K8S_DIR/10-postgres.yaml"
kubectl apply -f "$K8S_DIR/11-mongo.yaml"
kubectl apply -f "$K8S_DIR/12-redis.yaml"
kubectl apply -f "$K8S_DIR/13-kafka.yaml"
kubectl apply -f "$K8S_DIR/14-jaeger.yaml"

echo ""
echo "Waiting for infrastructure to become ready (up to 3 minutes)..."
kubectl wait --for=condition=ready pod -l app=postgres -n wallet-platform --timeout=180s
kubectl wait --for=condition=ready pod -l app=mongo    -n wallet-platform --timeout=180s
kubectl wait --for=condition=ready pod -l app=redis    -n wallet-platform --timeout=60s
kubectl wait --for=condition=ready pod -l app=kafka    -n wallet-platform --timeout=180s

echo ""
echo "[3/4] Deploying application services..."
kubectl apply -f "$K8S_DIR/20-wallet-service.yaml"
kubectl apply -f "$K8S_DIR/21-history-service.yaml"
kubectl apply -f "$K8S_DIR/22-exchange-rate-service.yaml"
kubectl apply -f "$K8S_DIR/23-api-gateway.yaml"

echo ""
echo "[4/4] Deploying Ingress and HPA..."
kubectl apply -f "$K8S_DIR/30-ingress-hpa.yaml"

echo ""
echo "=== Waiting for application pods to become ready ==="
kubectl wait --for=condition=ready pod -l app=wallet-service        -n wallet-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=history-service       -n wallet-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=exchange-rate-service -n wallet-platform --timeout=300s
kubectl wait --for=condition=ready pod -l app=api-gateway           -n wallet-platform --timeout=300s

echo ""
echo "=== Deploy complete ==="
echo ""
kubectl get pods,svc,ingress -n wallet-platform
echo ""
echo "Access via Ingress:"
echo "  minikube tunnel     # если minikube"
echo "  curl http://localhost/api/v1/..."
echo ""
echo "Or port-forward:"
echo "  kubectl port-forward -n wallet-platform svc/api-gateway 8000:8000"
echo "  curl http://localhost:8000/api/v1/..."

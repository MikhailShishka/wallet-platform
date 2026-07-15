# wallet-platform on Kubernetes

Манифесты для развёртывания wallet-platform в Kubernetes-кластере.

## Что внутри

| Файл | Что делает |
|---|---|
| `00-namespace.yaml` | Namespace `wallet-platform` |
| `10-postgres.yaml` | Postgres StatefulSet + Service + Secret |
| `11-mongo.yaml` | MongoDB StatefulSet + Service |
| `12-redis.yaml` | Redis Deployment + Service |
| `13-kafka.yaml` | Kafka в KRaft-режиме (без ZooKeeper), StatefulSet + Service |
| `14-jaeger.yaml` | Jaeger all-in-one для трассировки |
| `20-wallet-service.yaml` | wallet-service Deployment + Service + ConfigMap + Secret |
| `21-history-service.yaml` | history-service Deployment + Service + ConfigMap |
| `22-exchange-rate-service.yaml` | exchange-rate-service Deployment + Service + ConfigMap |
| `23-api-gateway.yaml` | api-gateway Deployment + Service + ConfigMap + Secret |
| `30-ingress-hpa.yaml` | Ingress для внешнего доступа + HPA для wallet-service |
| `deploy-all.sh` | Скрипт деплоя всей платформы |
| `teardown.sh` | Скрипт удаления |

## Требования

1. **Kubernetes-кластер** — minikube, k3d, kind, Docker Desktop Kubernetes, EKS, GKE.
2. **kubectl** настроен на этот кластер.
3. **Docker-образы** сервисов доступны кластеру.
4. **Ingress Controller** установлен (для minikube: `minikube addons enable ingress`).

## Быстрый старт с minikube

```bash
# 1. Запустить minikube с достаточно ресурсов
minikube start --cpus=4 --memory=8192

# 2. Использовать Docker daemon minikube (образы будут доступны кластеру без registry)
eval $(minikube docker-env)

# 3. Собрать образы всех 4 сервисов
cd ..
docker build -t wallet-service:1.0 wallet-service/
docker build -t history-service:1.0 history-service/
docker build -t exchange-rate-service:1.0 exchange-rate-service/
docker build -t api-gateway:1.0 api-gateway/

# 4. Включить Ingress
minikube addons enable ingress

# 5. Деплой всей платформы
cd k8s
chmod +x deploy-all.sh
./deploy-all.sh

# 6. Открыть Ingress
minikube tunnel      # в отдельном терминале
# или через port-forward:
kubectl port-forward -n wallet-platform svc/api-gateway 8000:8000

# 7. Проверить
curl http://localhost:8000/actuator/health
```

## Диагностика

```bash
# Все ресурсы платформы
kubectl get all -n wallet-platform

# Статус подов
kubectl get pods -n wallet-platform

# Логи конкретного пода
kubectl logs -n wallet-platform <pod-name>
kubectl logs -n wallet-platform -l app=wallet-service --tail=100

# Описание пода (события, состояние контейнеров)
kubectl describe pod -n wallet-platform <pod-name>

# Зайти внутрь пода
kubectl exec -it -n wallet-platform <pod-name> -- sh

# Порт-форвард для Jaeger UI
kubectl port-forward -n wallet-platform svc/jaeger 16686:16686
# затем http://localhost:16686
```

## Что демонстрирует деплой

- **StatefulSet** для stateful-инфры (Postgres, Mongo, Kafka) с PVC.
- **Deployment** для stateless-сервисов с Rolling Update (zero-downtime).
- **Service (ClusterIP)** для внутренней коммуникации между сервисами по DNS.
- **Ingress** для внешнего доступа только через api-gateway (defense in depth).
- **ConfigMap** для не-секретных настроек.
- **Secret** для паролей БД и JWT-ключей.
- **Startup/Liveness/Readiness probes** — K8s знает, когда pod готов принимать трафик.
- **Resources (requests + limits)** — scheduler правильно распределяет по нодам.
- **HPA** — автоскейлинг wallet-service по CPU/memory.
- **RollingUpdate стратегия** — обновление без даунтайма.

## Удаление

```bash
./teardown.sh
```

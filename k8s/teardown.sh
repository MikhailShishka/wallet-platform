#!/bin/bash
# ============================================================
# teardown.sh — удаление всей платформы из Kubernetes.
# Удаляет namespace целиком → все объекты внутри тоже.
# ============================================================

set -e

echo "This will DELETE the entire wallet-platform namespace and all its resources."
read -p "Are you sure? (yes/no): " confirm

if [ "$confirm" = "yes" ]; then
    kubectl delete namespace wallet-platform
    echo "Namespace wallet-platform deleted."
    echo "PersistentVolumes may need manual cleanup (kubectl get pv)."
else
    echo "Cancelled."
fi

#!/bin/bash
# ============================================================
# K8s Namespace Diff Test Setup Script
# Run from the k8s-diff-test/ directory
# Prerequisites: Docker Desktop running with Kubernetes enabled
# ============================================================

set -e

echo ""
echo "========================================"
echo " Step 1: Create Namespaces"
echo "========================================"
kubectl apply -f namespaces/namespaces.yaml
kubectl get namespaces | grep ns-

echo ""
echo "========================================"
echo " Step 2: Apply ns-alpha resources"
echo "========================================"
kubectl apply -f ns-alpha/configmaps/
kubectl apply -f ns-alpha/deployments/
kubectl apply -f ns-alpha/services/
kubectl apply -f ns-alpha/hpa/

echo ""
echo "========================================"
echo " Step 3: Apply ns-beta resources"
echo "========================================"
kubectl apply -f ns-beta/configmaps/
kubectl apply -f ns-beta/deployments/
kubectl apply -f ns-beta/services/
kubectl apply -f ns-beta/secrets/

echo ""
echo "========================================"
echo " Step 4: Install Kubernetes Dashboard"
echo "========================================"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml
kubectl apply -f dashboard-admin.yaml

echo ""
echo "========================================"
echo " Step 5: Wait for pods to be ready"
echo "========================================"
echo "Waiting for ns-alpha pods..."
kubectl wait --for=condition=ready pod -l app=nginx -n ns-alpha --timeout=90s
kubectl wait --for=condition=ready pod -l app=redis -n ns-alpha --timeout=90s

echo "Waiting for ns-beta pods..."
kubectl wait --for=condition=ready pod -l app=nginx -n ns-beta --timeout=90s
kubectl wait --for=condition=ready pod -l app=redis -n ns-beta --timeout=90s

echo ""
echo "========================================"
echo " Setup Complete!"
echo "========================================"
echo ""
echo "To open the Dashboard in your browser:"
echo ""
echo "  1. Get your login token:"
echo "     kubectl -n kube-system create token admin-user"
echo ""
echo "  2. Start proxy:"
echo "     kubectl proxy"
echo ""
echo "  3. Open browser:"
echo "     http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/"
echo ""
echo "  4. Select 'Token' login and paste the token from step 1."
echo ""
echo "Useful diff commands:"
echo "  kubectl get all -n ns-alpha"
echo "  kubectl get all -n ns-beta"
echo "  kubectl get configmap app-config -n ns-alpha -o yaml"
echo "  kubectl get configmap app-config -n ns-beta -o yaml"

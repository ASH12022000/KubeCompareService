#!/bin/bash
# Tear down everything created by apply-all.sh

echo "Deleting namespaces ns-alpha and ns-beta..."
kubectl delete namespace ns-alpha --ignore-not-found
kubectl delete namespace ns-beta --ignore-not-found

echo "Removing dashboard admin binding..."
kubectl delete clusterrolebinding admin-user-binding --ignore-not-found
kubectl delete serviceaccount admin-user -n kube-system --ignore-not-found

echo "Done. Namespaces and all their resources have been deleted."

# Kubernetes Configuration Comparison Tool

A full-stack application for comparing Kubernetes cluster configurations across different environments.

## Features
- **4-Step Wizard**: Credentials -> Scope -> Selections -> Results.
- **Connectivity**: Direct K8s API or SSH Jump Server tunneling.
- **Resource Comparison**: Deployments, ConfigMaps, PVCs, Services, Istio resources.
- **Security**: JWT Auth, Email OTP, Reversible Encryption for tokens, Rate Limiting, XSS Protection.
- **Advanced Tools**: YAML line-by-line diff, Baseline snapshots, PDF Export.

## Prerequisites
- **Java 17+**
- **Node.js 18+** & **Angular CLI**
- **MongoDB** (running on `localhost:27017`)
- **Maven**

## Setup & Running

### 1. Database
Ensure MongoDB is running locally:
```bash
# Example if using Docker
docker run -d -p 27017:27017 --name mongodb mongo
```

### 2. Backend (Spring Boot)
1. Navigate to the `backend` folder.
2. Update `src/main/resources/application.properties` with your real SMTP and Encryption keys.
3. Build and Run:
```bash
mvn clean install
mvn spring-boot:run
```
The API will be available at `http://localhost:8080`.

### 3. Frontend (Angular)
1. Navigate to the `frontend` folder.
2. Install dependencies:
```bash
npm install
```
3. Run the development server:
```bash
ng serve
```
The UI will be available at `http://localhost:4200`.

## Security Note
The application uses a custom encryption mechanism for K8s tokens. Ensure the `app.encryption.secret-key` in `application.properties` is kept secure and consistent.

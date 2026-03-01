# CloudShare

> Cloud-natives File-Sharing-Backend – vollständig auf AWS mit Terraform provisioniert.

Ein Spring Boot Backend das auf AWS ECS Fargate läuft, Dateien in S3 speichert, DynamoDB als Datenbank nutzt und AWS Cognito für Authentifizierung einsetzt. Das Projekt entstand mit dem Ziel, AWS-Infrastruktur und Infrastructure as Code in der Praxis zu erlernen – nicht primär das Feature-Set.

---

## Demo & Dokumentation

 **[Video-Erklärung](#)** – Vollständige Walkthrough des Projekts  
 **[GitHub Wiki](#)** – Detaillierte Design-Entscheidungen & Diagramme

---

## Tech Stack

### Backend

| Technologie | Zweck |
|---|---|
| Java 17 | Sprache |
| Spring Boot 3.4 | REST API, Business Logic |
| Spring Security + OAuth2 | JWT-Validierung via AWS Cognito |
| Spring Cloud AWS | S3 & DynamoDB Integration |
| AWS SDK v2 | Cognito Identity Provider Client |
| Docker | Containerisierung |

### AWS Infrastruktur

| Service | Zweck |
|---|---|
| ECS Fargate | Container-Hosting (serverless) |
| S3 | Datei-Storage + Frontend-Hosting |
| DynamoDB | NoSQL-Datenbank für Ordner, Dateien & Shares |
| Cognito | Authentifizierung & JWT-Ausstellung |
| ALB | HTTPS-Terminierung & Load Balancing |
| CloudFront | CDN für das React-Frontend |
| Lambda | Event-driven Cleanup-Service via DynamoDB Streams |
| Route 53 | DNS für `cloudshare-app.de` & `api.cloudshare-app.de` |
| ACM | SSL/TLS-Zertifikate |
| Athena + Glue | SQL-Abfragen auf ALB Access Logs |
| VPC | Netzwerk-Isolation mit VPC Endpoints |

### Infrastructure as Code & CI/CD

| Technologie | Zweck |
|---|---|
| Terraform | Provisionierung der gesamten AWS-Infrastruktur |
| GitHub Actions | CI/CD Pipeline (OIDC-basiert, kein statischer Key) |

---

## Highlights

**Event-driven Cleanup** – Wird ein Ordner gelöscht, bereinigt eine Lambda-Funktion automatisch alle zugehörigen S3-Objekte und DynamoDB-Einträge via DynamoDB Streams – asynchron, ohne den API-Request zu blockieren.

**Zwei Infrastruktur-Varianten** – Eine Production-Variante mit maximaler Sicherheit (private Subnetze, Interface Endpoints) und eine kostenoptimierte Live-Variante. Beide vollständig in Terraform definiert.

**Keine statischen AWS Keys** – Die CI/CD-Pipeline authentifiziert sich via OIDC direkt gegen AWS und nutzt temporäre Credentials zur Laufzeit.

**Defensiver Upload-Flow** – Token & Rollenprüfung → Quota-Check → S3-Upload → DynamoDB-Write mit automatischem Rollback bei Fehler.

---

## Tests

Testabdeckung > 80% auf drei Ebenen:

| Ebene | Scope | Framework |
|---|---|---|
| Unit Tests | Entities | JUnit 5, AssertJ |
| Integration Tests | Services | Spring Boot Test, Testcontainers, LocalStack |
| Web MVC Tests | Controllers | MockMvc, Spring Security Test |

---

## Projektstruktur

```
/
├── src/                    # Spring Boot Applikation
│   ├── controller/         # API Layer (HTTP, Routing, Security)
│   ├── service/            # Business Logic
│   ├── repository/         # DynamoDB Data Access Layer
│   └── ...
├── infra/
│   ├── production/         # Terraform – maximale Sicherheit
│   └── cheap/              # Terraform – kostenoptimiert (live)
└── .github/workflows/      # CI/CD Pipeline (GitHub Actions + OIDC)
```

---

## Weitere Dokumentation

Ausführliche Diagramme, Architekturentscheidungen und Infrastruktur-Details sind im **[GitHub Wiki](#)** dokumentiert.

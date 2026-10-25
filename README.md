# Parcelly — Courier Management System (MySQL edition)

A courier-management web application with a Java 21 backend, MySQL database, and responsive HTML/CSS/JavaScript frontend. The persistence layer now uses JDBC/MySQL; MongoDB is not required. JSON backup import/export remains available.

## Requirements
- Windows (for the included `.bat` launcher), Java 21 or later
- MySQL Server 8.0+ and MySQL Workbench (optional but useful for demonstrating the database)
- Internet access on first run to download MySQL Connector/J

## Setup
1. Install and start MySQL Server.
2. Open `schema.sql` in MySQL Workbench and execute it. This creates the `parcelly` database.
3. Copy `.env.example` to `.env` and set `MYSQL_USER` and `MYSQL_PASSWORD` to your MySQL credentials. Keep `.env` private.
4. Double-click `start.bat`.
5. Open `http://localhost:8080` and keep the terminal window running.

The app creates its tables automatically. In MySQL Workbench, refresh Schemas → `parcelly` → Tables. Tables include `customers`, `agents`, `accounts`, `parcels`, `tracking_events`, `payments`, and `workspace_metadata`. Core records have searchable relational columns; payload columns preserve the complete domain record for safe round-tripping through the existing application model.

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Administrator | `admin@parcelly.local` | `Admin@123` |
| Customer | `rahul@example.com` | `Customer@123` |

The initial empty database is seeded with demo records by default. Change `ADMIN_PASSWORD` in `.env` before using it beyond a classroom demo. Set `SEED_DEMO_DATA=false` to avoid inserting demo customers/parcels into a fresh database.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MYSQL_URL` | `jdbc:mysql://127.0.0.1:3306/parcelly?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC connection URL |
| `MYSQL_DATABASE` | `parcelly` | Database name |
| `MYSQL_USER` | `root` | MySQL username |
| `MYSQL_PASSWORD` | empty | MySQL password |
| `PORT` | `8080` | Web server port |
| `HOST` | `127.0.0.1` | Web server bind address |
| `ADMIN_EMAIL` | `admin@parcelly.local` | Initial admin account |
| `ADMIN_PASSWORD` | `Admin@123` | Initial admin password (local demo default) |
| `SEED_DEMO_DATA` | `true` | Seed sample records on first run |

## Backup and tests

Run `./test.ps1` for the domain and frontend tests. JSON backups can be exported/imported with the compiled classpath; see `src/courier/Backup.java` for command syntax. Import only works when the target database is empty.

## Data model

- `customers`: customer identity and searchable contact fields.
- `agents`: delivery agent identity and searchable contact fields.
- `accounts`: login role, linked customer, and salted password hash payload.
- `parcels`: tracking number, customer/agent IDs, status, recipient, service type, and full payload.
- `tracking_events`: one row per tracking-history event.
- `payments`: one row per parcel payment.
- `workspace_metadata`: schema version and revision used for concurrent-write conflict detection.

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes. The server enforces customer scope and admin-only operations. Use proper least-privilege MySQL credentials and HTTPS before deploying publicly.

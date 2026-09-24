# MMAS

Multi-modal authentication system using Spring Boot, PostgreSQL, WebAuthn, and
VOSK speaker recognition.

## Prerequisites

- Java 21
- PostgreSQL
- A browser that supports WebAuthn (for example, a recent Chrome, Edge, or
  Firefox)

## Local setup

### 1. Create the environment file

From the project root, copy the example file:

**PowerShell**

```powershell
Copy-Item .env.example .env
```

**Command Prompt**

```bat
copy .env.example .env
```

Open `.env` and set the credentials for your local PostgreSQL installation:

```dotenv
DB_USERNAME=postgres
DB_PASSWORD=your-postgresql-password
```

Do not commit `.env` or share its contents. The `.env.example` file is safe to
commit because it contains no credentials.

### 2. Create the PostgreSQL database

Create a database named `mmas` using `psql` or a PostgreSQL database client:

```sql
CREATE DATABASE mmas;
```

The application uses PostgreSQL on `localhost:5432` and creates or updates its
tables automatically when it starts.

### 3. Load the environment variables

Spring Boot reads `DB_USERNAME` and `DB_PASSWORD` from the process environment;
it does not load a `.env` file automatically. In PowerShell, load the values
into the current terminal session:

```powershell
$env:DB_USERNAME = (Get-Content .env | Where-Object { $_ -match '^DB_USERNAME=' }) -replace '^DB_USERNAME=', ''
$env:DB_PASSWORD = (Get-Content .env | Where-Object { $_ -match '^DB_PASSWORD=' }) -replace '^DB_PASSWORD=', ''
```

If you use IntelliJ IDEA, you can instead add `DB_USERNAME` and `DB_PASSWORD`
to the Run/Debug configuration's environment variables.

### 4. Start the application

Run the application from the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

Once it has started, open `http://localhost:8080` in your browser. Use
`/register` to enroll a user with WebAuthn and a voice sample, `/login` to
authenticate, and `/metrics` to view authentication metrics.

To stop the application, press `Ctrl+C` in the terminal running it.

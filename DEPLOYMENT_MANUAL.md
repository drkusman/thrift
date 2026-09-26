# Deploying ASUU-MOAUM Thrift to EC2 — manual build & install (no Docker)

This installs Java, Node.js, and PostgreSQL directly on the EC2 instance, builds the backend jar and
the frontend on the box itself, and runs both as systemd services behind nginx. nginx listens on port
80 and routes `/api/*` and `/actuator/*` to the backend (port 8090) and everything else to the frontend
(port 3000), so the app is reachable at `http://<EC2_PUBLIC_IP>` with no CORS setup needed.

(If you'd rather run this as containers instead, see [DEPLOYMENT.md](DEPLOYMENT.md) — both approaches
build the same code, just packaged differently.)

## 1. Launch the EC2 instance

Same as the Docker guide:
1. **EC2 → Launch instance** → name it, e.g. `thrift-prod`.
2. **AMI**: Ubuntu Server 24.04 LTS.
3. **Instance type**: `t3.small` (2 GB RAM) minimum — a JVM, Postgres, and a Node server all running
   at once will struggle on a 1 GB `t3.micro`.
4. **Key pair**: create one, download the `.pem`, keep it safe.
5. **Security group**: allow SSH (22) from **My IP** only, and HTTP (80) from `0.0.0.0/0`. Leave 8090,
   3000, and 5432 closed — nginx is the only public entry point, and Section 7 below binds the backend
   and frontend to `127.0.0.1` so they aren't reachable directly even if the security group changes later.
6. **Storage**: 20 GB gp3.
7. Launch it, note the **public IPv4 address** — that's `<EC2_PUBLIC_IP>` everywhere below.

## 2. Connect

```bash
chmod 400 thrift-prod-key.pem
ssh -i thrift-prod-key.pem ubuntu@<EC2_PUBLIC_IP>
```

Everything from here runs **on the instance**.

## 3. Install Java, Node.js, PostgreSQL, nginx, and git

```bash
sudo apt-get update

# Java 21
sudo apt-get install -y openjdk-21-jdk

# Node.js 20 LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# PostgreSQL, nginx, git
sudo apt-get install -y postgresql postgresql-contrib nginx git

# Confirm versions
java -version
node -v
psql --version
nginx -v
```

## 4. Set up PostgreSQL

```bash
sudo -u postgres psql
```

Inside the `psql` prompt:

```sql
ALTER USER postgres WITH PASSWORD 'CHANGE_ME_TO_A_STRONG_PASSWORD';
CREATE DATABASE thrift;
\q
```

Postgres listens on `localhost:5432` by default and isn't reachable from outside the instance unless
you deliberately reconfigure `postgresql.conf` — leave it as-is.

## 5. Get the code

If the GitHub repo is private, use a
[personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens):

```bash
git clone https://<your-github-username>:<token>@github.com/drkusman/thrift.git
git clone https://github.com/drkusman/thrift.git
cd thrift
```

## 6. Build and configure the backend

```bash
cd ~/thrift/backend
./mvnw package -DskipTests
```

This produces `target/thrift-0.0.1.jar`.

Now create the real config file (this is gitignored — it never goes near git, same as your own local
setup):

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
nano src/main/resources/application.yml
```

Edit:
- `spring.datasource.password` → the Postgres password you set in Section 4
- `thrift.admin.password` (the `THRIFT_ADMIN_PASSWORD:admin1234` default) → change `admin1234` to a
  real password, or leave the `${THRIFT_ADMIN_PASSWORD:...}` placeholder and set it via an environment
  variable in the systemd unit instead (Section 6a) — either works, pick one
- Add `server.address: 127.0.0.1` under `server:` so the backend only accepts connections from nginx
  on the same machine, not the outside world

The jar already has the *old* config baked in from before you edited the file — rebuild once more so
it picks up your changes:

```bash
./mvnw package -DskipTests
```

### 6a. Create the systemd service

```bash
sudo nano /etc/systemd/system/thrift-backend.service
```

```ini
[Unit]
Description=ASUU-MOAUM Thrift backend
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/thrift/backend
ExecStart=/usr/bin/java -jar /home/ubuntu/thrift/backend/target/thrift-0.0.1.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now thrift-backend
sudo systemctl status thrift-backend
```

Watch the startup log to confirm Flyway ran the migrations and the app started cleanly:

```bash
journalctl -u thrift-backend -f
```

## 7. Build and configure the frontend

```bash
cd ~/thrift/frontend
npm ci
NEXT_PUBLIC_API_URL="" npm run build
```

`output: "standalone"` (already set in `next.config.ts`) produces a minimal runtime in
`.next/standalone` that doesn't include the static assets or public files by default — copy them in:

```bash
cp -r public .next/standalone/
cp -r .next/static .next/standalone/.next/
```

**Every time you rebuild the frontend, repeat these two `cp` commands** — a fresh `npm run build`
overwrites `.next/standalone` without them.

### 7a. Create the systemd service

```bash
sudo nano /etc/systemd/system/thrift-frontend.service
```

```ini
[Unit]
Description=ASUU-MOAUM Thrift frontend
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/thrift/frontend/.next/standalone
Environment=PORT=3000
Environment=HOSTNAME=127.0.0.1
ExecStart=/usr/bin/node server.js
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now thrift-frontend
sudo systemctl status thrift-frontend
```

## 8. Configure nginx

```bash
sudo nano /etc/nginx/sites-available/thrift
```

```nginx
server {
    listen 80;
    client_max_body_size 50M;

    location /api/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/thrift /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
```

## 9. Verify

```bash
curl http://localhost/actuator/health
```

Should return `{"status":"UP"}`. From your own browser, visit `http://<EC2_PUBLIC_IP>` — you should see
the login page. Log in with the admin regno/password you set in `application.yml`, and change the
password immediately from inside the app.

## Common operations

**View logs:**
```bash
journalctl -u thrift-backend -f
journalctl -u thrift-frontend -f
sudo tail -f /var/log/nginx/error.log
```

**Restart a service:**
```bash
sudo systemctl restart thrift-backend
sudo systemctl restart thrift-frontend
```

**Deploy a new version after pushing code changes:**
```bash
cd ~/thrift
git pull

cd backend
./mvnw package -DskipTests
sudo systemctl restart thrift-backend

cd ../frontend
npm ci
NEXT_PUBLIC_API_URL="" npm run build
cp -r public .next/standalone/
cp -r .next/static .next/standalone/.next/
sudo systemctl restart thrift-frontend
```

**Back up the database:**
```bash
sudo -u postgres pg_dump thrift > ~/thrift-backup-$(date +%F).sql
```
Copy that file off the instance regularly (`scp` it to your own machine) — it's your only backup.

**Restore from a backup:**
```bash
sudo -u postgres psql thrift < ~/thrift-backup-2026-09-24.sql
```

## Adding a domain and HTTPS later

Once you have a domain, point an A record at `<EC2_PUBLIC_IP>`, then run
[Certbot](https://certbot.eff.org/) with the nginx plugin:

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.example.com
```

It edits the nginx config to terminate TLS and auto-renews the certificate. Open port 443 in the EC2
security group alongside port 80.

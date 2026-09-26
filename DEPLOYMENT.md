# Deploying ASUU-MOAUM Thrift to an AWS EC2 instance

This runs the whole stack — Postgres, the Spring Boot backend, the Next.js frontend, and an nginx
reverse proxy — as four Docker containers on one EC2 instance, managed by `docker compose`. nginx
listens on port 80 and routes `/api/*` to the backend and everything else to the frontend, so the
whole app is reachable over plain HTTP at the instance's public IP with no CORS setup needed (frontend
and backend look like the same origin to the browser).

This guide assumes no domain name yet — you'll access the app at `http://<EC2_PUBLIC_IP>`. See
[Adding a domain and HTTPS later](#adding-a-domain-and-https-later) for when you have one.

## 1. Launch the EC2 instance

1. In the AWS Console, go to **EC2 → Launch instance**.
2. **Name**: `thrift-prod` (or whatever you like).
3. **AMI**: Ubuntu Server 24.04 LTS (or 22.04) — free-tier eligible.
4. **Instance type**: `t3.small` at minimum. The stack runs Postgres, a JVM, and a Node server
   together; `t2.micro`/`t3.micro` (1 GB RAM) will swap heavily or OOM-kill containers. `t3.small`
   (2 GB) is a reasonable starting point for the alpha-testing phase; resize later if needed.
5. **Key pair**: create a new one (e.g. `thrift-prod-key`) and download the `.pem` file — you cannot
   re-download it later. Keep it somewhere safe; you'll need it for every SSH connection.
6. **Network settings → Edit** security group rules. Add:
   - SSH (port 22) — source: **My IP** (not `0.0.0.0/0` — don't leave SSH open to the whole internet)
   - HTTP (port 80) — source: `0.0.0.0/0` (anyone can reach the app)
   - Leave port 8090 (backend) and 5432 (Postgres) **closed** to the internet — nginx is the only
     public entry point; the other containers only talk to each other over Docker's internal network.
7. **Storage**: 20 GB gp3 is plenty to start.
8. Launch the instance. Note its **public IPv4 address** once it's running — you'll use it everywhere
   below in place of `<EC2_PUBLIC_IP>`.

## 2. Connect to the instance

```bash
chmod 400 thrift-prod-key.pem
ssh -i thrift-prod-key.pem ubuntu@<EC2_PUBLIC_IP>
```

Everything from here on runs **on the EC2 instance**, over this SSH session.

## 3. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Run docker without sudo (log out and back in after this for it to take effect)
sudo usermod -aG docker $USER
```

Log out (`exit`) and reconnect via SSH so the group change takes effect, then confirm:

```bash
docker --version
docker compose version
```

## 4. Get the code onto the instance

If the GitHub repo is private, either use a [deploy key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/managing-deploy-keys) or a
[personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) for cloning. With a token:

```bash
git clone https://<your-github-username>:<token>@github.com/drkusman/thrift.git
cd thrift
```

## 5. Configure secrets

```bash
cp .env.example .env
nano .env
```

Set real values for:
- `POSTGRES_PASSWORD` — a strong password, not the local dev one
- `THRIFT_ADMIN_REGNO` / `THRIFT_ADMIN_PASSWORD` — the bootstrap admin account (change the password
  via the app itself immediately after your first login in production)

`.env` is gitignored — it never leaves this instance.

## 6. Build and start the stack

```bash
docker compose up -d --build
```

First run takes a few minutes (compiling the backend jar, building the frontend, pulling the Postgres
and nginx images). Watch progress with:

```bash
docker compose logs -f
```

Flyway runs the database migrations automatically the first time the backend container starts —
you'll see `Successfully applied N migrations` in the backend logs (`docker compose logs backend`).

## 7. Verify it's up

```bash
curl http://localhost/actuator/health
```

Should return `{"status":"UP"}`. Then from your own browser, visit `http://<EC2_PUBLIC_IP>` — you
should see the login page.

Log in with the `THRIFT_ADMIN_REGNO` / `THRIFT_ADMIN_PASSWORD` you set in `.env`, and change the admin
password immediately from inside the app.

## Common operations

**View logs for one service:**
```bash
docker compose logs -f backend    # or frontend, postgres, nginx
```

**Restart everything:**
```bash
docker compose restart
```

**Deploy a new version after pushing code changes:**
```bash
git pull
docker compose up -d --build
```
This rebuilds only what changed and restarts those containers; Postgres data in the named volume is
untouched.

**Back up the database:**
```bash
docker compose exec postgres pg_dump -U postgres thrift > thrift-backup-$(date +%F).sql
```
Copy that file off the instance (e.g. `scp` it to your own machine) regularly — it's your only backup
unless you later move to RDS with automated snapshots.

**Restore from a backup:**
```bash
cat thrift-backup-2026-09-24.sql | docker compose exec -T postgres psql -U postgres thrift
```

**Stop everything (data persists in the volume):**
```bash
docker compose down
```

**Stop everything and delete the database too (irreversible):**
```bash
docker compose down -v
```

## Adding a domain and HTTPS later

Once you have a domain, point an A record at `<EC2_PUBLIC_IP>`, then swap nginx for a config that
terminates TLS — the simplest path is adding [Certbot](https://certbot.eff.org/) with the nginx plugin
on the host, or running an nginx container with a Let's Encrypt companion (e.g.
`nginxproxy/acme-companion`). Either way, HTTP (port 80) stays open for the ACME challenge, and you'd
open port 443 in the EC2 security group alongside it.

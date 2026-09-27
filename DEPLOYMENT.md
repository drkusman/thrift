# Deploying ASUU-MOAUM Thrift to AWS (EC2 + RDS, via Docker)

This runs the Spring Boot backend, the Next.js frontend, and an nginx reverse proxy as three Docker
containers on one EC2 instance, managed by `docker compose`. The database itself lives outside Docker,
on a managed **RDS PostgreSQL** instance - AWS handles its backups and patching, so the app container
never holds the only copy of your members' savings/loan data. nginx listens on port 80 and routes
`/api/*` to the backend and everything else to the frontend, so the whole app is reachable over plain
HTTP at the EC2 instance's public IP with no CORS setup needed (frontend and backend look like the same
origin to the browser).

This guide assumes no domain name yet - you'll access the app at `http://<EC2_PUBLIC_IP>`. See
[Adding a domain and HTTPS later](#adding-a-domain-and-https-later) for when you have one.

If you don't yet have an AWS account: go to [aws.amazon.com](https://aws.amazon.com), click **Create an
AWS account**, and follow their signup (you'll need an email, a phone number, and a card for their
identity verification - the resources below fit comfortably in the free tier or a few dollars a month,
but AWS does require a card on file). Nothing past this point can be done until that account exists and
you're signed into the AWS Console.

## 1. Create the RDS PostgreSQL database

1. AWS Console -> **RDS -> Databases -> Create database**.
2. **Engine type**: PostgreSQL. **Templates**: Free tier (or "Dev/Test" if free tier isn't offered in
   your region/account age).
3. **DB instance identifier**: `thrift-prod`.
4. **Master username**: `postgres` (or your own choice - just remember it for `.env` below).
5. **Master password**: generate a strong one and save it somewhere safe (a password manager, not a
   chat message) - you'll put it in `.env` on the EC2 instance, never in git.
6. **Instance class**: `db.t3.micro` (or `db.t4g.micro`) is enough to start.
7. **Storage**: 20 GB gp3, no need for autoscaling yet.
8. **Connectivity** -> **Compute resource**: choose "Don't connect to an EC2 compute resource" for now
   (you'll wire up security groups manually in step 3, once the EC2 instance exists) - or, if the
   console offers it after you've already launched the EC2 instance in step 2 below, you can pick "Connect
   to an EC2 compute resource" and let AWS wire the security group for you automatically.
9. **Public access**: **No** - the database should only ever be reachable from the EC2 instance, never
   from the open internet.
10. **Initial database name**: `thrift` (under "Additional configuration") - this pre-creates the
    database so you don't have to `CREATE DATABASE` by hand later.
11. Leave the rest at their defaults and click **Create database**. It takes a few minutes to become
    "Available".
12. Once it's available, click into it and note its **Endpoint** (e.g.
    `thrift-prod.xxxxxxxxxx.us-east-1.rds.amazonaws.com`) - that's `RDS_HOST` in `.env` later.

## 2. Launch the EC2 instance

1. AWS Console -> **EC2 -> Launch instance**.
2. **Name**: `thrift-prod`.
3. **AMI**: Ubuntu Server 24.04 LTS (or 22.04) - free-tier eligible.
4. **Instance type**: `t3.small` at minimum. The stack runs a JVM and a Node server together;
   `t2.micro`/`t3.micro` (1 GB RAM) will swap heavily or OOM-kill containers. `t3.small` (2 GB) is a
   reasonable starting point for the alpha-testing phase; resize later if needed.
5. **Key pair**: create a new one (e.g. `thrift-prod-key`) and download the `.pem` file - you cannot
   re-download it later. Keep it somewhere safe; you'll need it for every SSH connection.
6. **Network settings -> Edit** security group rules. Add:
   - SSH (port 22) - source: **My IP** (not `0.0.0.0/0` - don't leave SSH open to the whole internet)
   - HTTP (port 80) - source: `0.0.0.0/0` (anyone can reach the app)
   - Leave port 8090 (backend) closed to the internet - nginx is the only public entry point; the
     backend and frontend containers only talk to each other over Docker's internal network.
7. **Storage**: 20 GB gp3 is plenty to start.
8. Launch the instance. Note its **public IPv4 address** once it's running - you'll use it everywhere
   below in place of `<EC2_PUBLIC_IP>`.

## 3. Let the EC2 instance reach RDS

RDS's own security group needs an inbound rule allowing Postgres traffic from the EC2 instance:

1. AWS Console -> **RDS -> Databases -> thrift-prod -> Connectivity & security** -> click the VPC
   security group link.
2. **Inbound rules -> Edit inbound rules -> Add rule**:
   - Type: PostgreSQL (auto-fills port 5432)
   - Source: the EC2 instance's own security group (search for `thrift-prod` and pick the security
     group, not the instance) - this is safer than pasting an IP, since it keeps working if the EC2
     instance's IP ever changes.
3. Save.

## 4. Connect to the EC2 instance

```bash
chmod 400 thrift-prod-key.pem
ssh -i thrift-prod-key.pem ubuntu@<EC2_PUBLIC_IP>
```

Everything from here on runs **on the EC2 instance**, over this SSH session.

## 5. Install Docker

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

## 6. Get the code onto the instance

If the GitHub repo is private, either use a
[deploy key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/managing-deploy-keys)
or a
[personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
for cloning. With a token:

```bash
git clone https://<your-github-username>:<token>@github.com/drkusman/thrift.git
cd thrift
```

## 7. Configure secrets

```bash
cp .env.example .env
nano .env
```

Set real values for:
- `RDS_HOST` - the RDS endpoint you noted in step 1
- `RDS_PASSWORD` - the master password you set when creating the RDS instance
- `THRIFT_ADMIN_REGNO` / `THRIFT_ADMIN_PASSWORD` - the bootstrap admin account (change the password via
  the app itself immediately after your first login in production)

`.env` is gitignored - it never leaves this instance.

## 8. Build and start the stack

```bash
docker compose up -d --build
```

First run takes a few minutes (compiling the backend jar, building the frontend, pulling the nginx
image). Watch progress with:

```bash
docker compose logs -f
```

Flyway runs the database migrations automatically the first time the backend container starts against
RDS - you'll see `Successfully applied N migrations` in the backend logs
(`docker compose logs backend`).

## 9. Verify it's up

```bash
curl http://localhost/actuator/health
```

Should return `{"status":"UP"}`. Then from your own browser, visit `http://<EC2_PUBLIC_IP>` - you
should see the login page.

Log in with the `THRIFT_ADMIN_REGNO` / `THRIFT_ADMIN_PASSWORD` you set in `.env`, and change the admin
password immediately from inside the app.

## Common operations

**View logs for one service:**
```bash
docker compose logs -f backend    # or frontend, nginx
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
This rebuilds only what changed and restarts those containers.

**Back up the database:**

RDS takes automated daily snapshots by default (Console -> RDS -> Databases -> thrift-prod ->
Maintenance & backups) - that's your primary safety net, and lets you restore to any point within the
retention window without touching the EC2 instance at all. For an on-demand logical backup (e.g. before
a risky migration), run `pg_dump` from the EC2 instance against the RDS endpoint:

```bash
docker compose exec backend sh -c \
  'PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_dump -h '"$RDS_HOST"' -U "$SPRING_DATASOURCE_USERNAME" thrift' \
  > thrift-backup-$(date +%F).sql
```

(The backend image is Alpine-based and doesn't ship `pg_dump` by itself in this compose file - the
simplest one-off alternative is taking a manual RDS snapshot from the console instead: **RDS ->
Databases -> thrift-prod -> Actions -> Take snapshot**.)

**Stop everything:**
```bash
docker compose down
```
The database is unaffected either way, since it isn't part of this compose file - deleting it requires
a deliberate, separate action in the RDS console.

## Adding a domain and HTTPS later

Once you have a domain, point an A record at `<EC2_PUBLIC_IP>`, then swap nginx for a config that
terminates TLS - the simplest path is adding [Certbot](https://certbot.eff.org/) with the nginx plugin
on the host, or running an nginx container with a Let's Encrypt companion (e.g.
`nginxproxy/acme-companion`). Either way, HTTP (port 80) stays open for the ACME challenge, and you'd
open port 443 in the EC2 security group alongside it.

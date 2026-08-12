# Deploy to an Oracle Cloud Always-Free VM (free forever, always-on, HTTPS)

Runs the whole app (Spring Boot + its own Postgres + Caddy for automatic HTTPS) on one
Always-Free ARM VM. Result: a permanent `https://<your-ip>.sslip.io` demo at $0.

## Phase 1 — Create the VM (Oracle Cloud console)

1. Sign up at cloud.oracle.com (needs a card for identity verification; Always-Free isn't charged).
2. **Compute → Instances → Create Instance:**
   - Image: **Canonical Ubuntu 22.04**
   - Shape: **Ampere (Arm) — VM.Standard.A1.Flex**, set **2 OCPU / 12 GB** (within Always-Free).
     - If you see "Out of capacity", try a different Availability Domain or region, or retry later.
   - SSH keys: let it **generate a key pair and download the private key** (or paste your own public key).
   - Create. Note the instance's **public IP** (e.g. `140.238.1.2`).
3. **Open ports 80 & 443** in the network (one-time):
   - Instance → its **Subnet** → **Default Security List** → **Add Ingress Rules**:
     - Source `0.0.0.0/0`, IP Protocol **TCP**, Destination port **80**
     - Source `0.0.0.0/0`, IP Protocol **TCP**, Destination port **443**

## Phase 2 — SSH in, open the host firewall, install Docker

```bash
chmod 600 ~/Downloads/<your-key>.key
ssh -i ~/Downloads/<your-key>.key ubuntu@<public-ip>
```

Oracle's Ubuntu image has a host firewall that blocks everything but SSH — open 80/443:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Install Docker + compose:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker   # or log out/in so `docker` works without sudo
```

## Phase 3 — Get the code onto the VM

Easiest (and good for your portfolio) — push the repo to GitHub once, then clone:

```bash
# on the VM:
git clone https://github.com/<you>/expense-approval.git
cd expense-approval/deploy
```

(Or from your Mac: `rsync -av --exclude target --exclude node_modules ~/Desktop/resume/expense-approval/ ubuntu@<ip>:~/expense-approval/`)

## Phase 4 — Configure and launch

In `deploy/`, create `.env` (dashes in the IP; sslip.io turns it into a hostname for free):

```bash
cat > .env <<EOF
DB_PASSWORD=$(openssl rand -hex 16)
APP_JWT_SECRET=$(openssl rand -base64 48)
SITE_ADDRESS=$(curl -s ifconfig.me | tr '.' '-').sslip.io
EOF
cat .env   # note the SITE_ADDRESS — that's your demo URL
```

Build + run the whole stack:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

First build takes a few minutes (Node + Maven). Watch it come up:

```bash
docker compose -f docker-compose.prod.yml logs -f app     # wait for "Started ExpenseApprovalApplication"
```

## Phase 5 — Verify

Open `https://<your-ip-with-dashes>.sslip.io` — Caddy will have fetched a Let's Encrypt cert
automatically. Log in with a demo account (alice@acme.com / password123).

## Updating later

```bash
cd ~/expense-approval && git pull
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

## Notes
- Always-on, so **no cold start** — instant every time, $0 forever.
- Caddy auto-renews the TLS cert; data persists in the `expense_pg` volume.
- If you later want a real domain, point it at the IP and change `SITE_ADDRESS`.

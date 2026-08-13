# Deploy to an Oracle Cloud Always-Free VM (free forever, always-on, HTTPS)

Runs the whole app (Spring Boot + its own Postgres + Caddy for automatic HTTPS) on one
Always-Free ARM VM, at $0.

Two ways to get a hostname for the TLS certificate:

- **sslip.io** — zero setup. `1-2-3-4.sslip.io` resolves to `1.2.3.4`, so the URL is derived
  from the VM's IP. Fine for a throwaway demo, but the URL changes if the IP does.
- **DuckDNS** (what the live demo uses) — a free subdomain plus a cron job that re-points it
  whenever the IP changes, so the URL stays stable. See Phase 4b.

The live instance runs at <https://lijuantang-expense.duckdns.org>.

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

### Phase 4b — A stable hostname with DuckDNS (what the live demo uses)

sslip.io ties the URL to the IP, so the demo link breaks if the VM's address changes. DuckDNS
gives a free subdomain you can re-point instead. Register a subdomain at duckdns.org, then set
`SITE_ADDRESS=<your-subdomain>.duckdns.org` in `.env` and keep it pointed at the VM:

```bash
# refresh the DNS record every 5 minutes in case the IP changes
( crontab -l 2>/dev/null; \
  echo "*/5 * * * * curl -s 'https://www.duckdns.org/update?domains=<subdomain>&token=<token>&ip=' >/dev/null" \
) | crontab -
```

Caddy requests the certificate for whatever `SITE_ADDRESS` says, so nothing else changes.

Build + run the whole stack:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

First build takes a few minutes (Node + Maven). Watch it come up:

```bash
docker compose -f docker-compose.prod.yml logs -f app     # wait for "Started ExpenseApprovalApplication"
```

## Phase 5 — Verify

Open whatever `SITE_ADDRESS` you configured — `https://<your-ip-with-dashes>.sslip.io`, or your
DuckDNS subdomain. Caddy will have fetched a Let's Encrypt cert automatically. Log in with a demo
account (alice@acme.com / password123).

## Updating later

```bash
cd ~/expense-approval && git pull
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

## Notes
- Always-on, so **no cold start** — instant every time, $0 forever.
- Caddy auto-renews the TLS cert; data persists in the `expense_pg` volume.
- If you later want a real domain, point it at the IP and change `SITE_ADDRESS`.

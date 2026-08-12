#!/usr/bin/env bash
# One-shot VM setup: opens the host firewall, installs Docker, generates secrets, and brings
# up the full stack (app + Postgres + Caddy auto-HTTPS). Safe to re-run.
# Usage on the VM:  cd ~/expense-approval/deploy && bash bootstrap.sh
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Opening host firewall for 80/443 (Oracle images block these by default)"
if command -v firewall-cmd >/dev/null 2>&1 && sudo systemctl is-active --quiet firewalld; then
  # Oracle Linux (firewalld)
  sudo firewall-cmd --permanent --add-port=80/tcp
  sudo firewall-cmd --permanent --add-port=443/tcp
  sudo firewall-cmd --reload
else
  # Ubuntu/Debian (iptables + netfilter-persistent)
  sudo iptables -C INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || \
    sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
  sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || \
    sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
  command -v netfilter-persistent >/dev/null 2>&1 && sudo netfilter-persistent save || true
fi

echo "==> Installing Docker if needed"
if ! command -v docker >/dev/null 2>&1; then
  if command -v dnf >/dev/null 2>&1; then
    # Oracle Linux / RHEL 9 — the get.docker.com script doesn't support 'ol', use the repo directly.
    sudo dnf install -y dnf-plugins-core || sudo dnf install -y dnf-utils || true
    sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null || \
      sudo dnf config-manager addrepo --from-repofile=https://download.docker.com/linux/centos/docker-ce.repo
    sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    sudo systemctl enable --now docker
  else
    curl -fsSL https://get.docker.com | sudo sh
  fi
  sudo usermod -aG docker "$USER" || true
fi

echo "==> Writing .env if missing"
if [ ! -f .env ]; then
  IP="$(curl -s ifconfig.me)"
  {
    echo "DB_PASSWORD=$(openssl rand -hex 16)"
    echo "APP_JWT_SECRET=$(openssl rand -base64 48)"
    echo "SITE_ADDRESS=$(echo "$IP" | tr '.' '-').sslip.io"
  } > .env
fi
echo "    Demo URL will be: https://$(grep -E '^SITE_ADDRESS=' .env | cut -d= -f2)"

echo "==> Building + starting the stack (first build takes a few minutes)"
sudo docker compose -f docker-compose.prod.yml up -d --build

echo "==> Done. Follow the app boot with:"
echo "    sudo docker compose -f docker-compose.prod.yml logs -f app"

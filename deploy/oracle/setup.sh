#!/usr/bin/env bash
# Sets up Shop Billing on a fresh Ubuntu server (Oracle Cloud "Always Free" VM): Java, the app as a service that
# starts on boot and restarts if it stops, and Caddy for https with a free certificate.
#
# Before running: copy these to the server's home folder (see README.md in this folder):
#   shop-billing.jar        (target/shop-billing-1.0.0.jar, renamed)
#   shop-billing.service    (this folder)
#   setup.sh                (this file)
#   billingdb.mv.db         (optional: your data/billingdb.mv.db, to move the shop's data across)
#
# Run:  sudo bash setup.sh your-name.duckdns.org
set -euo pipefail

DOMAIN="${1:?usage: sudo bash setup.sh your-name.duckdns.org}"
APP_DIR=/opt/shop-billing
HERE="$(cd "$(dirname "$0")" && pwd)"

[ -f "$HERE/shop-billing.jar" ] || { echo "shop-billing.jar not found next to setup.sh"; exit 1; }
[ -f "$HERE/shop-billing.service" ] || { echo "shop-billing.service not found next to setup.sh"; exit 1; }

echo "== Installing Java 17, git and Caddy"
apt-get update
apt-get install -y openjdk-17-jre-headless git curl gnupg debian-keyring debian-archive-keyring apt-transport-https iptables-persistent
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt -o /etc/apt/sources.list.d/caddy-stable.list
apt-get update
apt-get install -y caddy

echo "== Installing the app in $APP_DIR"
id shopbilling >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin shopbilling
mkdir -p "$APP_DIR/data" "$APP_DIR/backups"
systemctl stop shop-billing 2>/dev/null || true
install -m 644 "$HERE/shop-billing.jar" "$APP_DIR/shop-billing.jar"
if [ -f "$HERE/billingdb.mv.db" ]; then
    if [ -f "$APP_DIR/data/billingdb.mv.db" ]; then
        cp "$APP_DIR/data/billingdb.mv.db" "$APP_DIR/backups/billingdb.before-upload.$(date +%Y%m%d_%H%M%S).mv.db"
    fi
    install -m 640 "$HERE/billingdb.mv.db" "$APP_DIR/data/billingdb.mv.db"
    echo "   shop data copied"
fi
chown -R shopbilling:shopbilling "$APP_DIR"
install -m 644 "$HERE/shop-billing.service" /etc/systemd/system/shop-billing.service

echo "== https for $DOMAIN"
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
    encode gzip
    reverse_proxy 127.0.0.1:8080
}
EOF

# Oracle's Ubuntu images block web traffic in the server firewall; open ports 80 and 443 (once).
for port in 80 443; do
    iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
        || iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
netfilter-persistent save

systemctl daemon-reload
systemctl enable --now shop-billing
systemctl restart caddy

echo
echo "Done. Give the app about a minute to start, then open:  https://$DOMAIN"
echo "Check it with:   sudo systemctl status shop-billing      Logs:  sudo journalctl -u shop-billing -f"

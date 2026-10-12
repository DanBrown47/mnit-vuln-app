#!/usr/bin/env bash
# Upload server/ and build the venv on the VPS. Does NOT enable services —
# the enable/nginx/cert/cron steps are run with eyes open (see README.md),
# because the box runs another app we must not disturb.
# Password: set VPS_PASS in your shell env (sshpass) or let ssh prompt. Never hardcode.
set -euo pipefail
HOST="${VPS_HOST:-31.42.125.200}"
SSHOPTS="-o StrictHostKeyChecking=accept-new"
run() {
    if [ -n "${VPS_PASS:-}" ]; then sshpass -e ssh $SSHOPTS root@"$HOST" "$@";
    else ssh $SSHOPTS root@"$HOST" "$@"; fi
}

echo "[*] sanity: existing services + port 8099"
run "ss -ltnp | grep -E ':(80|443|8099) ' || true; systemctl is-active nginx || true"

echo "[*] upload server/ to /opt/mnit-vulnapp/server/"
run "mkdir -p /opt/mnit-vulnapp"
if [ -n "${VPS_PASS:-}" ]; then
    sshpass -e rsync -az --delete -e "ssh $SSHOPTS" \
        "$(dirname "$0")/../server/" root@"$HOST":/opt/mnit-vulnapp/server/
else
    rsync -az --delete -e "ssh $SSHOPTS" \
        "$(dirname "$0")/../server/" root@"$HOST":/opt/mnit-vulnapp/server/
fi
# also copy deploy/ so the unit file is on the box
if [ -n "${VPS_PASS:-}" ]; then
    sshpass -e rsync -az -e "ssh $SSHOPTS" \
        "$(dirname "$0")/" root@"$HOST":/opt/mnit-vulnapp/deploy/
else
    rsync -az -e "ssh $SSHOPTS" \
        "$(dirname "$0")/" root@"$HOST":/opt/mnit-vulnapp/deploy/
fi

echo "[*] venv + deps + seed db"
run bash -s <<'REMOTE'
set -euo pipefail
apt-get update -qq
apt-get install -y -qq python3-venv nginx certbot python3-certbot-nginx rsync >/dev/null
python3 -m venv /opt/mnit-vulnapp/venv
/opt/mnit-vulnapp/venv/bin/pip install -q -r /opt/mnit-vulnapp/server/requirements.txt
cd /opt/mnit-vulnapp/server
rm -f tea.db
/opt/mnit-vulnapp/venv/bin/python -c "import db; db.init_db('tea.db'); db.seed('tea.db')"
echo "venv + db ready"
REMOTE

echo "[*] upload done. Run the enable/nginx/cert/cron steps from deploy/README.md."

# Deploy — MNIT Vuln App

Target: `android.danwand.in` (VPS `31.42.125.200`). The box runs another app —
**do not disturb it**. Everything here is additive: a new service on port 8099,
a new nginx server block, a new cron line.

## Credentials

Never commit the root password. Supply it at run time:

```bash
export VPS_PASS='…'      # used by sshpass inside deploy.sh; unset it afterwards
# or omit it and let ssh prompt you interactively
```

## 0. Pre-flight (change nothing)

```bash
ssh root@31.42.125.200
systemctl list-units --type=service --state=running
ss -ltnp                 # confirm what owns 80/443 and that 8099 is free
ls /etc/nginx/sites-enabled
timedatectl              # note the timezone for the teardown cron
```

If `8099` is taken, pick another free port and change it in BOTH
`mnit-vulnapp.service` and `nginx-mnit-vulnapp.conf` before continuing.

## 1. Upload code + build venv

```bash
cd deploy
VPS_HOST=31.42.125.200 ./deploy.sh
```

This uploads `server/`, installs `python3-venv nginx certbot`, builds the venv,
and seeds `tea.db`. It does **not** enable any service.

## 2. Enable the service (new unit only)

```bash
ssh root@31.42.125.200
cp /opt/mnit-vulnapp/deploy/mnit-vulnapp.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now mnit-vulnapp
systemctl status mnit-vulnapp --no-pager
curl -s localhost:8099/health          # -> {"status":"ok"}
```

## 3. Add the nginx server block (new file only — don't edit existing vhosts)

```bash
cp /opt/mnit-vulnapp/deploy/nginx-mnit-vulnapp.conf /etc/nginx/sites-available/mnit-vulnapp
ln -sf /etc/nginx/sites-available/mnit-vulnapp /etc/nginx/sites-enabled/
nginx -t                               # must pass; existing vhosts stay listed
systemctl reload nginx
```

## 4. TLS (this domain only)

```bash
certbot --nginx -d android.danwand.in --non-interactive --agree-tos \
        -m danwand47@gmail.com --redirect
certbot certificates                   # confirm only android.danwand.in changed
```

## 5. Teardown cron — stop ONLY this service at session end

The service's clock is **UTC**. 11:00 PM IST on 2026-10-14 is **17:30 UTC**, so
the cron minute/hour is `30 17` (see `deploy/teardown.cron`). If the box is in a
different timezone, recompute from `timedatectl`. Append, never overwrite root's
crontab:

```bash
( crontab -l 2>/dev/null; \
  echo '30 17 14 10 * systemctl stop mnit-vulnapp.service && systemctl disable mnit-vulnapp.service' ) | crontab -
crontab -l
```

## 6. Verify the exploit end-to-end

```bash
TOKEN=mnit_app_2026_bearer_9f3c1a
curl -s https://android.danwand.in/api/users -H "Authorization: Bearer $TOKEN"
curl -s https://android.danwand.in/api/tea -H "Authorization: Bearer $TOKEN" -H "X-User-Id: 1"
# -> contains flag{This is often harder than real world NGL.}
curl -s -o /dev/null -w "%{http_code}\n" https://android.danwand.in/api/users   # -> 401
```

Then confirm the pre-existing app still responds on its own URL/port.

## Teardown is automatic

The cron stops + disables `mnit-vulnapp` at session end. nginx and the other
app keep running. To remove fully later: delete the systemd unit, the nginx
symlink + conf, run `certbot delete --cert-name android.danwand.in`, and
`rm -rf /opt/mnit-vulnapp`.

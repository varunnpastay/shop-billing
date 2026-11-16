# Running Shop Billing free on Oracle Cloud (never sleeps)

Oracle Cloud's **Always Free** tier gives you a small Linux server that runs 24/7 at no cost, with a permanent disk.
The app runs there exactly as on the shop PC (same built-in database file), behind **https** with a free certificate.

You need: an Oracle Cloud account, a free web address from DuckDNS, and about 30 minutes once.

---

## 1. Create the free server

1. Sign up at <https://www.oracle.com/cloud/free/>. A card is needed to verify you, but Always Free resources are not
   charged. Pick the home region nearest to you (e.g. **India West (Mumbai)** or **India South (Hyderabad)**). It
   cannot be changed later.
2. **Compute → Instances → Create instance**
   - Image: **Canonical Ubuntu 22.04** (or 24.04)
   - Shape: **Ampere → VM.Standard.A1.Flex**, 1 OCPU and 6 GB memory (Always Free). If Ampere is "out of capacity",
     use **VM.Standard.E2.1.Micro** (also Always Free) or try again later.
   - Networking: keep "Assign a public IPv4 address" on.
   - SSH keys: **Save private key**. Keep this file safe; it is the only way into the server.
3. Note the instance's **Public IP address**.
4. Open the web ports: on the instance page click the **Subnet → Security List → Add Ingress Rules**, then add two rules.
   Each uses Source CIDR `0.0.0.0/0` and IP protocol TCP:
   - Destination port **80**
   - Destination port **443**

## 2. Get a free web address

1. Sign in at <https://www.duckdns.org> (with Google or GitHub).
2. Add a sub-domain, e.g. `svtextiles` → you get **svtextiles.duckdns.org**.
3. Put the server's **Public IP** in the "current ip" box and click **update ip**.

## 3. Copy the app to the server

On the shop PC, open PowerShell in the `shop-billing` folder. Replace `KEY` with your saved key file and `IP` with the
public IP.

```powershell
mvn -q -DskipTests package                                   # makes target\shop-billing-1.0.0.jar
copy target\shop-billing-1.0.0.jar deploy\oracle\shop-billing.jar
scp -i KEY deploy\oracle\shop-billing.jar deploy\oracle\shop-billing.service deploy\oracle\setup.sh ubuntu@IP:~
```

**To move the shop's existing data as well**, close the app on the shop PC first (close the black window), then run:

```powershell
scp -i KEY data\billingdb.mv.db ubuntu@IP:~
```

(Remove the sample data first if you added it. Restore the folder `backups\data_before_sample_*` as `data`.)

## 4. Install

```powershell
ssh -i KEY ubuntu@IP
```

Then, on the server:

```bash
sudo bash setup.sh svtextiles.duckdns.org
```

After about a minute, open **https://svtextiles.duckdns.org** on any phone or computer and log in.

- If you started without your data, the login is `admin` / `admin123`. **Change the password immediately**
  (Settings → Password), because the app is now on the internet.

## 5. Daily Excel backup to GitHub (from the server)

The server needs permission to push to your **private** data repository:

1. GitHub → Settings → Developer settings → **Fine-grained tokens → Generate new token**
   - Repository access: **Only select repositories** → `shop-billing-data`
   - Permissions: **Contents → Read and write**
   - Copy the token.
2. On the server (replace `YOUR-GITHUB-NAME` and `TOKEN`):

   ```bash
   sudo -u shopbilling git config --global credential.helper store
   echo "https://YOUR-GITHUB-NAME:TOKEN@github.com" | sudo -u shopbilling tee /opt/shop-billing/.git-credentials >/dev/null
   sudo chmod 600 /opt/shop-billing/.git-credentials
   ```

3. In the app: **Settings → Daily Excel backup to GitHub** → paste the repository link → **Save link** →
   **Export & push now**.

## Updating the app later

On the shop PC: build, then copy the new jar to the server:

```powershell
copy target\shop-billing-1.0.0.jar deploy\oracle\shop-billing.jar
scp -i KEY deploy\oracle\shop-billing.jar ubuntu@IP:~
```

Then on the server:

```bash
sudo install -m 644 -o shopbilling ~/shop-billing.jar /opt/shop-billing/shop-billing.jar
sudo systemctl restart shop-billing
```

## Useful commands (on the server)

| What | Command |
|---|---|
| Is it running? | `sudo systemctl status shop-billing` |
| Live log | `sudo journalctl -u shop-billing -f` |
| Restart | `sudo systemctl restart shop-billing` |
| Data file | `/opt/shop-billing/data/billingdb.mv.db` |
| Automatic backups (every 6 h) | `/opt/shop-billing/backups/` |

## Important

- **Use one place only.** Once the shop uses the cloud address, stop using the app on the shop PC (or the other way
  round). Otherwise there will be two separate sets of bills.
- **Needs internet.** If the shop's internet is down, billing stops. Printing still happens on the shop's printer
  through the browser.
- **It is on the internet now.** Use a strong password. Consider turning the wrong-password lockout back on.
- **DuckDNS address:** the IP of an Oracle instance stays the same while the instance exists. If you ever recreate the
  server, update the IP on DuckDNS.

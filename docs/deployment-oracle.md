# Deploying free on Oracle Cloud Always Free

Oracle's Always Free tier includes an ARM VM with up to 4 cores and 24 GB RAM
that runs permanently at no cost. That is far more than this application needs,
and unlike free tiers that sleep, it stays up - so there is no cold start when
your client opens the app at the gym.

Everything runs on that one VM behind Caddy, which terminates HTTPS and serves
the app and the API from **one origin**. That is deliberate: session cookies are
then same-origin, so there is no SameSite issue and no CORS to configure.

    browser ──HTTPS──> Caddy ─┬─ /api/*  ──> backend  (Spring Boot)
                              └─ /*       ──> frontend (Next.js)
                                              postgres, redis (internal only)

## 1. Create the VM

1. Sign up at cloud.oracle.com. A card is required for identity verification;
   Always Free resources are not charged. **Pick your home region carefully -
   it cannot be changed later.**
2. Compute -> Instances -> Create instance.
   - Image: **Ubuntu 22.04**
   - Shape: **VM.Standard.A1.Flex** (Ampere/ARM) - this is the Always Free one.
     Give it 2 OCPU and 12 GB; you can go to 4/24 if capacity allows.
   - Save the SSH private key it offers. You cannot download it again.
3. Networking -> the instance's subnet -> Security List -> add ingress rules
   allowing **TCP 80** and **TCP 443** from `0.0.0.0/0`.

If instance creation fails with "out of capacity", that is normal for the free
ARM shape - try a different availability domain, or retry later.

## 2. Point a hostname at it

Caddy needs a real hostname to get a certificate. A registered domain is
easiest (about $12/year), but a free dynamic-DNS hostname from **duckdns.org**
works with Let's Encrypt and costs nothing.

Create an `A` record pointing at the instance's public IP, then confirm it
resolves before continuing:

```bash
dig +short your-host.duckdns.org
```

## 3. Prepare the server

SSH in (`ssh -i your-key.pem ubuntu@YOUR_IP`), then:

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker ubuntu && newgrp docker

# Ubuntu images on Oracle ship with restrictive iptables rules; the security
# list alone is not enough to let traffic reach the host.
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 4. Deploy

```bash
git clone https://github.com/amrkasaOSU/fitnesshub.git
cd fitnesshub
cp .env.prod.example .env
nano .env          # set SITE_ADDRESS and a generated POSTGRES_PASSWORD

docker compose -f docker-compose.prod.yml up -d --build
```

The first build compiles both images on the VM and takes roughly 5-10 minutes
on 2 ARM cores. Watch it with:

```bash
docker compose -f docker-compose.prod.yml logs -f
```

Caddy requests a certificate on first start. Once it succeeds, the site is live
on HTTPS.

## 5. Verify before your client sees it

```bash
curl -s https://your-host/actuator/health          # {"status":"UP"}
```

Then in a browser:

1. There are **no demo accounts** in production - register your own coach
   account at `/register`.
2. Build a program under **Programs -> New program**.
3. Add your client, then open the program and **assign** it to them. A client
   with no program cannot log workouts.
4. Sign in as the client with the temporary password, change it, and log one
   set yourself before handing the account over.

## Operating it

**Backups.** The database holds client body weight and check-in notes. Nothing
backs it up automatically:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U fitnesshub fitnesshub | gzip > ~/fitnesshub-$(date +%F).sql.gz
```

Worth putting in a weekly cron job and copying off the VM. Restoring:

```bash
gunzip -c backup.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U fitnesshub fitnesshub
```

**Updating** after pushing changes:

```bash
git pull && docker compose -f docker-compose.prod.yml up -d --build
```

Flyway applies new migrations on boot. Existing data is preserved - the volumes
are not touched by a rebuild.

**Logs:** `docker compose -f docker-compose.prod.yml logs -f backend`

**Certificate renewal** is automatic. It depends on the `caddy_data` volume, so
don't delete that.

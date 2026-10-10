# Free Deployment Guide

This guide explains how to deploy the Social Media Platform for free using modern cloud services.

## Architecture for Free Tier

| Component | Provider | Plan |
|-----------|----------|------|
| **Database (PostgreSQL)** | [Neon](https://neon.tech) | Free Tier (0.5 vCPU, 2GB RAM) |
| **Cache (Redis)** | [Upstash](https://upstash.com) | Free Tier (Max 10K requests/day) |
| **Events (Kafka)** | [Aiven](https://aiven.io) | Free Tier (Apache Kafka) |
| **Backend (Spring Boot)** | [Render](https://render.com) or [Koyeb](https://koyeb.com) | Free Instance (Spin-down after inactivity) |
| **Frontend (React)** | [Vercel](https://vercel.com) or [Netlify](https://netlify.com) | Free Tier |

---

## 1. Database Setup (Neon)

1. Create an account at [Neon.tech](https://neon.tech).
2. Create a new project and a database named `socialmedia`.
3. Copy the **Connection String** (Pooled preferred). It looks like:
   `postgres://user:password@ep-cool-darkness-123456-pooler.us-east-2.aws.neon.tech/socialmedia?sslmode=require`

## 2. Redis & Kafka Setup

### Redis (Upstash)
1. Create an account at [Upstash](https://upstash.com).
2. Create a **Redis** database.
3. Copy the **Endpoint** (host and port) and **Password**.

### Kafka (Aiven)
1. Create an account at [Aiven.io](https://aiven.io).
2. Create a new project and select **Apache Kafka**.
3. Choose the **Free Plan** and a region (e.g., `aws-us-east-1` to match Neon).
4. Once the service is running, go to the **Overview** tab and copy the **Service URI** (this is your Bootstrap Server).
5. In the **Authentication Method**, Aiven Free Tier typically uses **SASL** (Scram-SHA-256).
6. Create a user (e.g., `avnadmin`) and copy the **Password**.
7. The app creates its own topic (`notification-events`) on startup via a Spring `NewTopic`
   bean, as long as the Aiven user has topic-create permissions - you shouldn't need to
   create it by hand, but if the app fails to start with a topic-authorization error, create
   `notification-events` manually as a fallback.

---

## 3. Backend Deployment (Render)

1. Fork the repository to your GitHub account.
2. Create a new **Web Service** on [Render](https://render.com).
3. Connect your GitHub repository.
4. Set the following:
   - **Root Directory**: `backend`
   - **Runtime**: `Docker`
5. Add **Environment Variables**:
   - `DATABASE_URL`: `jdbc:postgresql://your-neon-host:5432/socialmedia?sslmode=require` (Convert the Neon string to JDBC format)
   - `DB_USER`: `your-neon-user`
   - `DB_PASSWORD`: `your-neon-password`
   - `REDIS_HOST`: `your-upstash-redis-host`
   - `REDIS_PORT`: `your-upstash-redis-port`
   - `REDIS_PASSWORD`: `your-upstash-redis-password`
   - `REDIS_SSL_ENABLED`: `true` (Upstash requires TLS; this defaults to `false` for local
     Docker Compose Redis, so it must be set explicitly here)
   - `KAFKA_SERVERS`: `your-aiven-kafka-service-uri` (e.g., `kafka-xyz.aivencloud.com:port`)
   - `KAFKA_SASL_MECHANISM`: `SCRAM-SHA-256`
   - `KAFKA_SECURITY_PROTOCOL`: `SASL_SSL`
   - `KAFKA_JAAS_CONFIG`: `org.apache.kafka.common.security.scram.ScramLoginModule required username="avnadmin" password="YOUR_AIVEN_PASSWORD";`
   - `JWT_SECRET`: Generate a random 256-bit string.
   - `FRONTEND_URL`: your Vercel URL (e.g. `https://your-app.vercel.app`) - only needed if
     you're using Google OAuth2 login, since that's where the backend redirects after a
     successful login.

---

## 4. Frontend Deployment (Vercel)

1. Create a new project on [Vercel](https://vercel.com).
2. Connect your GitHub repository.
3. Set the following:
   - **Root Directory**: `frontend`
   - **Framework Preset**: `Vite`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
4. Add **Environment Variables**:
   - `VITE_API_BASE_URL`: `https://your-backend-render-url.onrender.com/api`

---

## 5. Image Uploads

There is no server-side image upload or object storage integration in this codebase - posts
just take a plain `imageUrl` string. If you want real image hosting, host the file yourself
(e.g. upload directly to a free-tier S3/Cloudinary/imgbb bucket from the frontend) and paste
the resulting URL into the post; wiring actual S3 upload into the backend is a reasonable
follow-up, not something currently implemented here.

---

## Important Notes

- **Cold Starts**: Render's free tier spins down after 15 minutes of inactivity. The first request might take 30-60 seconds.
- **Upstash Limits**: Monitor your usage in the Upstash console. If you exceed the free tier limits, services might start failing.
- **CORS**: Ensure your backend's CORS configuration allows your Vercel URL. You can set this in `WebSecurityConfig.java` or via an environment variable if implemented.

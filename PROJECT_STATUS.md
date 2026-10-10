# Project Status

This file used to describe the project as "production-ready" with fabricated performance
numbers and a stale completion breakdown. That's been replaced with what's actually true as
of the last audit - see [docs/VERIFICATION_REPORT.md](docs/VERIFICATION_REPORT.md) for the
full history of what was found and fixed, and [README.md](README.md) for the current feature
list and known limitations.

## What's real

- Backend: all core features (auth, posts, follow, feed, notifications, Kafka, Redis, WebSocket)
  are implemented and covered by unit/controller tests plus Testcontainers integration tests
  (`mvn verify`, requires Docker) - see the test counts in the verification report rather than
  a hand-maintained percentage here, since those drift out of date immediately.
- Frontend: Login, Register, Feed, Profile, Followers, Create Post, and Notifications pages
  are all implemented against the real backend, not placeholders. Settings is still a
  placeholder ("coming soon").
- DevOps: Docker Compose starts the full stack; CI runs backend tests (including
  Testcontainers integration tests, since GitHub-hosted runners have Docker), frontend
  build/test, and a full docker-compose end-to-end script (`scripts/verify.sh`).

## What's not done / explicitly out of scope

- Image upload is a URL string field, not a file upload + object storage pipeline.
- No AWS ECS/S3/CloudFront - see README's Known Limitations for why that's not just
  unfinished, but intentionally not built here.
- No ranking/ML personalization on the feed - it's chronological, following-based.
- OAuth2 login code is real (issues an app JWT on success) but isn't runtime-verified in CI,
  since that requires live Google credentials this repo doesn't have.

## Performance numbers

Previous versions of this file listed specific millisecond timings for feed queries, post
creation, etc. Those were never actually benchmarked and have been removed rather than
carried forward as fact. If you need real numbers, measure them against your own deployment -
don't trust ones that were never measured here.

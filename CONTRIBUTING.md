# Contributing

Contributions are welcome when they improve the actual application behavior, test coverage, security posture, or developer experience.

## Workflow

1. Create a feature branch from `main`.
2. Keep changes focused and explain the reason for the patch in the pull request.
3. Run the relevant backend and frontend checks before submitting.
4. Add or update tests for fixes and behavior changes.
5. Document environment assumptions and any limitations.

## Local verification

Backend:

```bash
cd backend
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn test -Dtest='!*IntegrationTest'
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm test -- --run
npm run build
```

Docker-level integration tests require a working Docker daemon and are therefore best run in CI or a local environment with Docker enabled.

## Pull requests

- Keep the scope narrow and reviewable.
- Explain the bug or enhancement clearly.
- Include verification evidence in the PR description.
- Avoid shipping credentials or environment secrets.

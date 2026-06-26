# Contributing

Thank you for taking the time to contribute.

## Getting Started

1. Fork the repository and clone your fork.
2. Start the local environment: `docker compose up -d`
3. Run tests: `./mvnw test`
4. Make your changes on a feature branch: `git checkout -b feat/your-feature`
5. Ensure all tests pass and add new tests for any new behaviour.
6. Open a pull request against `main`.

## Code Style

- Java: 4-space indentation, no wildcard imports, no Lombok.
- YAML: 2-space indentation.
- Use the `.editorconfig` settings — most IDEs pick these up automatically.

## Testing

- Unit tests live alongside the code in `src/test/java/`.
- Concurrency tests go in the `concurrency` package.
- Integration tests use Testcontainers and require Docker.
- All unit tests must pass before a PR is merged: `./mvnw test -Dtest="!FlashSaleIntegrationTest"`.

## Commit Messages

Use conventional commits:
- `feat: add X` — new feature
- `fix: correct Y` — bug fix
- `refactor: simplify Z` — no behaviour change
- `test: cover case W` — test only
- `docs: update README` — documentation only
- `chore: bump dependency` — maintenance

## Reporting Issues

Use the issue templates in `.github/ISSUE_TEMPLATE/`. Include steps to reproduce for bugs.

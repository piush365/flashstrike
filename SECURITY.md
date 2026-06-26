# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| main    | Yes       |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Email **piushgogi@gmail.com** with:
- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fixes

You will receive a response within 48 hours. If the issue is confirmed, a patch will be released as quickly as possible and you will be credited (unless you prefer anonymity).

## Security Considerations

This project is intended as a portfolio/learning system. Before deploying to production:

- Replace the default `API_KEY` with a strong, randomly generated value.
- Store all secrets in a secrets manager (AWS Secrets Manager, Vault, etc.) — never in environment files or source code.
- Restrict Redis and Kafka ports to the internal network only.
- Enable TLS for all inter-service communication.
- Review and tighten CORS settings if a browser UI is added.
- Consider replacing API-key authentication with OAuth 2.0 / JWT for multi-tenant deployments.

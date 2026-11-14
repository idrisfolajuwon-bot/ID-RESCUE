# IDRIS AI STUDIO — ChatGPT MCP integration

This folder contains the first remote MCP server draft for connecting IDRIS AI STUDIO to ChatGPT.

## What it exposes

- \`ask_idris_ai\` — sends a text question to the existing IDRIS AI STUDIO backend.
- \`create_image\` — sends an image prompt to the existing IDRIS AI STUDIO image endpoint and returns the PNG as MCP image content.

The server is a wrapper around the existing IDRIS AI STUDIO backend. It does not replace the website, Supabase sign-in, or current AI backend.

## Current status

- Source code: draft
- Public deployment: not deployed
- ChatGPT connection: not configured
- OpenAI directory review: not submitted

## Security and cost controls

- Request bodies and prompt lengths are limited.
- Two Cloudflare rate-limit bindings are configured: per-client and per-location global limits.
- If rate-limit bindings are missing or fail, tool execution fails closed.
- The server does not save conversations or image prompts itself.
- The existing AI backend may perform live web research when relevant and may process prompts through its configured AI/search providers.
- The public endpoint must be reviewed and tested before directory submission. Rate limits are approximate and are not a substitute for authentication or abuse monitoring.

## Deployment

Deploy only after the account owner has reviewed the code and any applicable Cloudflare usage costs.

The Worker uses the Cloudflare Workers rate-limit bindings configured in \`wrangler.toml\`. The public MCP endpoint will be \`https://<worker-host>/mcp\`; the health check will be \`https://<worker-host>/health\`.

## ChatGPT availability

OpenAI's custom MCP connection and public app-directory workflows have separate eligibility and verification requirements. A deployed MCP endpoint does not automatically become a public ChatGPT app. See the official OpenAI Apps SDK and plugin submission documentation before submitting.

## Product identity

IDRIS AI STUDIO is an independent I D LABS product. It is not ChatGPT and is not affiliated with or endorsed by OpenAI.

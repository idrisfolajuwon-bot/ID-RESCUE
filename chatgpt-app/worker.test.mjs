import test from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

async function post(payload, headers = {}, env = {}) {
  const request = new Request("https://example.test/mcp", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(payload)
  });
  const response = await worker.fetch(request, env);
  return { response, body: response.status === 202 ? null : await response.json() };
}

test("health endpoint advertises the two IDRIS tools", async () => {
  const response = await worker.fetch(new Request("https://example.test/health"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ok, true);
  assert.deepEqual(body.tools, ["ask_idris_ai", "create_image"]);
});

test("unknown route returns 404", async () => {
  const response = await worker.fetch(new Request("https://example.test/other"), {});
  assert.equal(response.status, 404);
});

test("OPTIONS returns CORS preflight headers", async () => {
  const response = await worker.fetch(new Request("https://example.test/mcp", { method: "OPTIONS" }), {});
  assert.equal(response.status, 204);
  assert.match(response.headers.get("Access-Control-Allow-Methods"), /POST/);
});

test("malformed JSON returns a parse error", async () => {
  const response = await worker.fetch(new Request("https://example.test/mcp", {
    method: "POST",
    body: "{",
    headers: { "Content-Type": "application/json" }
  }), {});
  assert.equal(response.status, 400);
  const body = await response.json();
  assert.equal(body.error.code, -32700);
});

test("legacy MCP initialization negotiates a supported protocol version", async () => {
  const { response, body } = await post({
    jsonrpc: "2.0", id: 1, method: "initialize",
    params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } }
  });
  assert.equal(response.status, 200);
  assert.equal(body.result.protocolVersion, "2025-03-26");
  assert.equal(body.result.serverInfo.name, "idris-ai-studio");
});

test("legacy tools/list returns both tools", async () => {
  const { body } = await post({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} });
  assert.deepEqual(body.result.tools.map(tool => tool.name), ["ask_idris_ai", "create_image"]);
});

test("modern MCP discovery returns the advertised capability set", async () => {
  const { response, body } = await post(
    {
      jsonrpc: "2.0", id: 3, method: "server/discover",
      params: { _meta: {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {}
      } }
    },
    { "MCP-Protocol-Version": "2026-07-28", "Mcp-Method": "server/discover" }
  );
  assert.equal(response.status, 200);
  assert.equal(body.result.resultType, "complete");
  assert.deepEqual(body.result.supportedVersions, ["2026-07-28"]);
  assert.ok(body.result.capabilities.tools);
});

test("modern tools/list includes result metadata", async () => {
  const { body } = await post(
    {
      jsonrpc: "2.0", id: 4, method: "tools/list",
      params: { _meta: {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {}
      } }
    },
    { "MCP-Protocol-Version": "2026-07-28", "Mcp-Method": "tools/list" }
  );
  assert.equal(body.result.resultType, "complete");
  assert.equal(body.result.tools.length, 2);
});

test("tool execution fails closed when rate-limit bindings are not configured", async () => {
  const { body } = await post({
    jsonrpc: "2.0", id: 5, method: "tools/call",
    params: { name: "ask_idris_ai", arguments: { question: "Say hello." } }
  });
  assert.equal(body.result.isError, true);
  assert.match(body.result.content[0].text, /request limit/);
});

test("oversized declared requests are rejected before parsing", async () => {
  const response = await worker.fetch(new Request("https://example.test/mcp", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Content-Length": "16001" },
    body: "{}"
  }), {});
  assert.equal(response.status, 413);
});

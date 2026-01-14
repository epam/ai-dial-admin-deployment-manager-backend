UPDATE mcp_deployment
SET transport = 'HTTP_STREAMING'
WHERE transport = 'STDIO';
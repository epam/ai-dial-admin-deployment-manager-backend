#------------------------------------------------------------------------------
# Stage 1: Source Artifact Image
# This stage imports a pre-existing Docker image, defined by the template
# variable {{MCP_PROXY_HOLDER}}. This source image is expected to contain the
# pre-built executable at the path /mcp_proxy. This stage is
# aliased as "artifact-holder" to serve as a clean source for the final image.
#------------------------------------------------------------------------------
FROM {{MCP_PROXY_HOLDER_IMAGE}} AS artifact-holder

#------------------------------------------------------------------------------
# Stage 2: Final Runtime Image
# This stage builds the final, runnable image by layering the pre-built
# executable on top of a new base image.
#------------------------------------------------------------------------------
FROM {{BASE_IMAGE}}

COPY --from=artifact-holder --chmod=0755 /mcp_proxy /usr/local/bin/mcp-proxy

ENV PATH=/usr/local/bin/:$PATH

ENTRYPOINT [ \
    "mcp-proxy", \
    "--port", "8080", \
    "--host", "0.0.0.0", \
    "--pass-environment", \
    "--transport", "streamablehttp", \
    "--stateless", \
    "--", \
    {{BASE_IMAGE_COMMAND}}\
]
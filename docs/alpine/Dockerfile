#------------------------------------------------------------------------------
# Stage 1: The "Base Builder" - Creates a musllinux image with a shared Python
#------------------------------------------------------------------------------
FROM quay.io/pypa/musllinux_1_2_x86_64 AS base-builder

# Define a Python version and installation destination
ARG PYTHON_VERSION=3.10.13
ARG PYTHON_PREFIX=/opt/python-shared

# Install dependencies required to build Python from source using apk
# 'build-base' includes gcc, make, etc.
RUN apk add --no-cache \
    build-base binutils wget zlib-dev bzip2-dev openssl-dev \
    ncurses-dev sqlite-dev readline-dev tk-dev gdbm-dev \
    libffi-dev xz-dev

# Download, build, and install a version of Python with the --enable-shared flag
RUN wget "https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tar.xz" && \
    tar -xf "Python-${PYTHON_VERSION}.tar.xz" && \
    cd "Python-${PYTHON_VERSION}" && \
    ./configure --prefix=${PYTHON_PREFIX} --enable-shared --with-ensurepip=install && \
    make -j$(nproc) && \
    make install && \
    cd / && \
    rm -rf "Python-${PYTHON_VERSION}" "Python-${PYTHON_VERSION}.tar.xz"

# Set environment variables to use our newly compiled Python
# LD_LIBRARY_PATH is crucial for the system to find the new libpython.so
ENV PATH="${PYTHON_PREFIX}/bin:${PATH}"
ENV LD_LIBRARY_PATH="${PYTHON_PREFIX}/lib:${LD_LIBRARY_PATH}"


#------------------------------------------------------------------------------
# Stage 2: The "MCP Proxy Builder" - Uses the shared Python to build the executable
# This stage starts from the previous one, so it's fast.
#------------------------------------------------------------------------------
FROM base-builder AS mcp-proxy-builder

# Upgrade pip and install pyinstaller + mcp-proxy
RUN python3 -m pip install --no-cache-dir --upgrade pip
RUN python3 -m pip install --no-cache-dir pyinstaller mcp-proxy==0.10.0

# Set working dir for build artifacts
WORKDIR /build

# Build single-file executable with PyInstaller
# Using $(which mcp-proxy) is a robust way to find the executable path
RUN pyinstaller \
    --noconfirm \
    --onefile \
    --name mcp_proxy_executable \
    $(which mcp-proxy)


#------------------------------------------------------------------------------
# Stage 2: The "Artifact Holder" - An empty stage containing only the executable
# This creates a clean, minimal "source" for the final copy operation.
#------------------------------------------------------------------------------
FROM busybox AS artifact-holder

COPY --from=mcp-proxy-builder /build/dist/mcp_proxy_executable /mcp_proxy

#!/bin/sh
set -eu

DEFAULTS_FILE="${defaultsFile}"
JVM_OPTIONS_FILE="${jvmOptionsFile}"
MAIN_JAR="${mainExecutable}"

if [ -r "${DEFAULTS_FILE}" ]; then
    set -a
    . "${DEFAULTS_FILE}"
    set +a
fi

if [ -n "${JAVA_HOME:-}" ]; then
    PATH="${JAVA_HOME}/bin:${PATH}"
    export PATH
fi

JAVA="$(command -v java || true)"

if [ "${1:-}" = "--reload" ]; then
    SOCKET_FILE="/run/${packageName}/control.sock"
    if [ ! -S "${SOCKET_FILE}" ]; then
        echo "Error: Debian control socket not found: ${SOCKET_FILE}. Is ${systemdServiceName} running?" >&2
        exit 1
    fi
    if command -v python3 >/dev/null 2>&1; then
        exec python3 -c "
import socket, sys
try:
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
        s.settimeout(10.0)
        s.connect('${SOCKET_FILE}')
        s.sendall(b'RELOAD\n')
        data = s.recv(4096).decode('utf-8', errors='replace')
        if data.startswith('OK'):
            sys.stdout.write(data)
            sys.exit(0)
        else:
            sys.stderr.write(data if data else 'Error: Empty response from reload server\n')
            sys.exit(1)
except Exception as e:
    sys.stderr.write(f'Error communicating with Debian control socket (${SOCKET_FILE}): {e}\n')
    sys.exit(1)
"
    fi
    if [ -z "${JAVA}" ] || [ ! -x "${JAVA}" ]; then
        echo "Error: Java execution environment not found." >&2
        echo "Please ensure 'java' is in your PATH or JAVA_HOME is correctly set." >&2
        exit 1
    fi
    MAIN_DIR="$(dirname "${MAIN_JAR}")"
    CP="${MAIN_JAR}"
    if [ -d "${MAIN_DIR}/lib/main" ]; then
        CP="${MAIN_DIR}/lib/main/*:${MAIN_DIR}/app/*:${CP}"
    fi
    exec "${JAVA}" -cp "${CP}" io.github.rawvoid.quarkus.debian.packaging.runtime.socket.DebianReloadClient "${SOCKET_FILE}"
fi

if [ -z "${JAVA}" ] || [ ! -x "${JAVA}" ]; then
    echo "Error: Java execution environment not found." >&2
    echo "Please ensure 'java' is in your PATH or JAVA_HOME is correctly set." >&2
    exit 1
fi

if [ -f "${JVM_OPTIONS_FILE}" ]; then
    JVM_OPTS=""
    while read -r line || [ -n "$line" ]; do
        case "$line" in
            \#*|"") continue ;;
            *) JVM_OPTS="${JVM_OPTS:+${JVM_OPTS} }${line}" ;;
        esac
    done < "${JVM_OPTIONS_FILE}"
    
    if [ -n "${JVM_OPTS}" ]; then
        export JDK_JAVA_OPTIONS="${JVM_OPTS}"
    fi
fi

exec "${JAVA}" -jar "${MAIN_JAR}" "$@"

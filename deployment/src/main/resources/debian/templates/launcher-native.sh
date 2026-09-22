#!/bin/sh
set -eu

DEFAULTS_FILE="${defaultsFile}"
MAIN_EXECUTABLE="${mainExecutable}"

if [ -r "${DEFAULTS_FILE}" ]; then
    set -a
    . "${DEFAULTS_FILE}"
    set +a
fi

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
    echo "Error: python3 is required to send reload command to ${SOCKET_FILE} in native mode." >&2
    exit 1
fi

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

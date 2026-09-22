#!/bin/sh
set -eu

DEFAULTS_FILE="${defaultsFile}"
MAIN_EXECUTABLE="${mainExecutable}"
INSTALL_DIR="${installDir}"

if [ -r "${DEFAULTS_FILE}" ]; then
    set -a
    . "${DEFAULTS_FILE}"
    set +a
fi

if [ "${1:-}" = "--reload" ]; then
    shift
    if [ ! -x "${INSTALL_DIR}/reload" ]; then
        echo "Error: Reload helper script not found or not executable: ${INSTALL_DIR}/reload" >&2
        exit 1
    fi
    exec "${INSTALL_DIR}/reload" "$@"
fi

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

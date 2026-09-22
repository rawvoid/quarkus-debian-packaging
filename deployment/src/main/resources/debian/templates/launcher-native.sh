#!/bin/sh
set -eu

DEFAULTS_FILE="${defaultsFile}"
MAIN_EXECUTABLE="${mainExecutable}"

if [ -r "${DEFAULTS_FILE}" ]; then
    set -a
    . "${DEFAULTS_FILE}"
    set +a
fi

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

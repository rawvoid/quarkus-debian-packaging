#!/bin/sh
set -eu

INSTALL_DIR="${installDir}"

if [ "${1:-}" = "--reload" ]; then
    shift
    if [ ! -x "${INSTALL_DIR}/reload" ]; then
        echo "Error: Reload helper script not found or not executable: ${INSTALL_DIR}/reload" >&2
        exit 1
    fi
    exec "${INSTALL_DIR}/reload" "$@"
fi

if [ ! -r "${INSTALL_DIR}/environment" ]; then
    echo "Error: Environment script not found: ${INSTALL_DIR}/environment" >&2
    exit 1
fi
. "${INSTALL_DIR}/environment"

MAIN_EXECUTABLE="${mainExecutable}"

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

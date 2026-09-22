#!/bin/sh
set -eu

MAIN_EXECUTABLE="${mainExecutable}"

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

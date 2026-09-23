#!/bin/sh
set -eu

QUARKUS_RUNNER="${quarkusRunner}"

if [ ! -x "${QUARKUS_RUNNER}" ]; then
    echo "Error: native runner not found or not executable: ${QUARKUS_RUNNER}" >&2
    exit 1
fi

exec "${QUARKUS_RUNNER}" "$@"

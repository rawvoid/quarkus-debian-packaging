#!/bin/sh
set -eu

INSTALL_DIR="${installDir}"

case "${1:-}" in
    --reload)
        shift
        if [ ! -x "${INSTALL_DIR}/reload" ]; then
            echo "Error: Reload helper script not found or not executable: ${INSTALL_DIR}/reload" >&2
            exit 1
        fi
        exec "${INSTALL_DIR}/reload" "$@"
        ;;
esac

DEFAULTS_FILE="${defaultsFile}"
if [ -r "${DEFAULTS_FILE}" ]; then
    while read -r line || [ -n "$line" ]; do
        line="${line#export }"
        case "$line" in
            \#* | \;* | "" ) continue ;;
            *=* )
                key="${line%%=*}"
                val="${line#*=}"
                case "$val" in
                    \"*\" | \'*\' )
                        val="${val#?}"
                        val="${val%?}"
                        ;;
                esac
                export "${key}=${val}"
                ;;
            * )
                echo "Error: Invalid line in ${DEFAULTS_FILE} (missing '='): '${line}'" >&2
                exit 1
                ;;
        esac
    done < "${DEFAULTS_FILE}"
fi

if [ ! -x "${INSTALL_DIR}/startup" ]; then
    echo "Error: Startup script not found or not executable: ${INSTALL_DIR}/startup" >&2
    exit 1
fi

exec "${INSTALL_DIR}/startup" "$@"

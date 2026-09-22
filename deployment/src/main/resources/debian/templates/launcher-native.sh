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

DEFAULTS_FILE="${defaultsFile}"
MAIN_EXECUTABLE="${mainExecutable}"

if [ -r "${DEFAULTS_FILE}" ]; then
    while read -r line || [ -n "$line" ]; do
        case "$line" in
            \#* | \;* | "" ) continue ;;
            *=* ) ;;
            * ) continue ;;
        esac
        key="${line%%=*}"
        val="${line#*=}"
        case "$key" in
            export\ * )
                key="${key#export}"
                key="${key#"${key%%[![:blank:]]*}"}"
                ;;
        esac
        key="${key%"${key##*[![:blank:]]}"}"
        case "$key" in
            "" | [0-9]* | *[!a-zA-Z0-9_]* ) continue ;;
        esac
        case "$val" in
            \"*\" | \'*\' )
                val="${val#?}"
                val="${val%?}"
                ;;
            * )
                val="${val#"${val%%[![:blank:]]*}"}"
                val="${val%"${val##*[![:blank:]]}"}"
                ;;
        esac
        export "${key}=${val}"
    done < "${DEFAULTS_FILE}"
fi

if [ ! -x "${MAIN_EXECUTABLE}" ]; then
    echo "Error: native executable not found or not executable: ${MAIN_EXECUTABLE}" >&2
    exit 1
fi

exec "${MAIN_EXECUTABLE}" "$@"

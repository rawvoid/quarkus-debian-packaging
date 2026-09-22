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
JVM_OPTIONS_FILE="${jvmOptionsFile}"
MAIN_JAR="${mainExecutable}"

if [ -r "${DEFAULTS_FILE}" ]; then
    while read -r line || [ -n "$line" ]; do
        line="${line#export }"
        case "$line" in
            \#* | \;* | "" ) continue ;;
            *=* ) export "${line%%=*}=${line#*=}" ;;
            * )
                echo "Error: Invalid line in ${DEFAULTS_FILE} (missing '='): '${line}'" >&2
                exit 1
                ;;
        esac
    done < "${DEFAULTS_FILE}"
fi

if [ -n "${JAVA_HOME:-}" ]; then
    PATH="${JAVA_HOME}/bin:${PATH}"
    export PATH
fi

JAVA="$(command -v java || true)"

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

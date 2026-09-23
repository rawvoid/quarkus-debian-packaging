#!/bin/sh
set -eu

INSTALL_DIR="${installDir}"
SYSTEMD_SERVICE_NAME="${systemdServiceName}"

case "${1:-}" in
    --reload)
        shift
        if [ ! -x "${INSTALL_DIR}/reload" ]; then
            echo "Error: Configuration hot-reload is disabled for this build (helper script not found: ${INSTALL_DIR}/reload)." >&2
            echo "Hint: Rebuild the package with -Dquarkus.debian.reload.enabled=true to enable it." >&2
            exit 1
        fi
        exec "${INSTALL_DIR}/reload" "$@"
        ;;
    --status|status)
        shift
        if ! command -v systemctl >/dev/null 2>&1; then
            echo "Error: systemctl command not found. Cannot query service status." >&2
            exit 1
        fi
        exec systemctl status "${SYSTEMD_SERVICE_NAME}" "$@"
        ;;
    --version|-v)
        echo "${packageName} ${version} (${architecture})"
        exit 0
        ;;
    --help|-h)
        cat << 'EOF'
Usage: ${packageName} [OPTIONS|COMMAND]

Commands / Options:
    --reload         Hot-reload configuration via UNIX domain socket (requires quarkus.debian.reload.enabled=true)
    --status, status Query systemd service status
    --version, -v    Print package version and architecture
    --help, -h       Print this help message

Running without arguments starts the application in the foreground.
To manage the background service, use systemd:
    sudo systemctl {start|stop|restart|status|reload} ${systemdServiceName}

Configuration:
    Config File:     ${configFile}
    Environment:     ${defaultsFile}
EOF
        exit 0
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

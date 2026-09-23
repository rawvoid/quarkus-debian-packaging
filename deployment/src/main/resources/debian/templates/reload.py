#!/usr/bin/env python3
import os
import socket
import sys

def main():
    socket_file = sys.argv[1] if len(sys.argv) > 1 else "${socketPath}"
    if not os.path.exists(socket_file):
        sys.stderr.write(f"Error: Control socket not found: {socket_file}. Is ${systemdServiceName} running?\n")
        sys.exit(1)

    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
            s.settimeout(10.0)
            s.connect(socket_file)
            s.sendall(b"RELOAD\n")
            chunks = []
            while True:
                chunk = s.recv(4096)
                if not chunk:
                    break
                chunks.append(chunk)
            data = b"".join(chunks).decode("utf-8", errors="replace")
            if data.startswith("OK"):
                sys.stdout.write(data)
                sys.exit(0)
            else:
                sys.stderr.write(data if data else "Error: Empty response from reload server\n")
                sys.exit(1)
    except Exception as e:
        sys.stderr.write(f"Error communicating with control socket ({socket_file}): {e}\n")
        sys.exit(1)

if __name__ == "__main__":
    main()

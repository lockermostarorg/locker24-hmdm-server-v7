FROM ubuntu:24.04
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends systemd systemd-sysv openssh-server openssh-client git ca-certificates openjdk-17-jre-headless procps && rm -rf /var/lib/apt/lists/*
RUN systemctl enable ssh
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]

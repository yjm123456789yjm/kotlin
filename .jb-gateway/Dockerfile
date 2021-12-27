FROM ubuntu:20.04

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

ARG ZULU_REPO_VER=1.0.0-2

RUN apt-get -qq update && \
    apt-get -qq -y --no-install-recommends install gnupg software-properties-common locales curl && \
    locale-gen en_US.UTF-8 && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0x219BD9C9 && \
    curl -sLO https://cdn.azul.com/zulu/bin/zulu-repo_${ZULU_REPO_VER}_all.deb && dpkg -i zulu-repo_${ZULU_REPO_VER}_all.deb && \
    apt-get -qq update && \
    apt-get -qq -y dist-upgrade && \
    mkdir -p /usr/share/man/man1 && \
    # JDK 6
    apt-get -qqy install zulu-6 && \
    # JDK 7
    echo "Package: zulu7-*\nPin: version 7.50.0+11*\nPin-Priority: 1001" > /etc/apt/preferences && \
    apt-get -qq -y --no-install-recommends install zulu7-jdk=7.0.322-* && \
    # JDK 8
    echo "Package: zulu8-*\nPin: version 8.58.0+13*\nPin-Priority: 1001" > /etc/apt/preferences && \
    apt-get -qq -y --no-install-recommends install zulu8-jdk=8.0.312-* && \
    # JDK 9
    apt-get -qqy install zulu-9 && \
    \
    apt-get -qq -y purge gnupg software-properties-common curl && \
    apt -y autoremove && \
    rm -rf /var/lib/apt/lists/* zulu-repo_${ZULU_REPO_VER}_all.deb

ENV JAVA_HOME=/usr/lib/jvm/zulu8-ca-amd64

# New naming conventions
ENV JDK6=/usr/lib/jvm/zulu6-ca-amd64 \
    JDK7=/usr/lib/jvm/zulu7-ca-amd64 \
    JDK8=/usr/lib/jvm/zulu8-ca-amd64 \
    JDK9=/usr/lib/jvm/zulu9-ca-amd64
ENV JAVA_HOME=$JDK8

# TeamCity JDK old naming conventions
ENV JDK_16=$JDK6 JDK_17=$JDK7 JDK_18=$JDK8 JDK_9=$JDK9
ENV JDK_16_x64=$JDK6 JDK_17_x64=$JDK7 JDK_18_x64=$JDK8 JDK_9_x64=$JDK9

# Dev Environment
RUN apt update && apt install --no-install-recommends -y git ssh lsof
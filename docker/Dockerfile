# Stage 1: Get Gurobi (fixed to version 12)

FROM gurobi/optimizer:12.0.0 AS gurobi-base

# Stage 2: Build final image

FROM ubuntu:24.04

# Copy Gurobi from base image

COPY --from=gurobi-base /opt/gurobi /opt/gurobi

# Versionen & URLs

ENV SCIP_DEB_FILENAME=SCIPOptSuite-9.2.4-Linux-ubuntu24.deb
ENV SCIP_DEB_URL=https://github.com/scipopt/scip/releases/download/v924/${SCIP_DEB_FILENAME}
ENV SCIP_DEB_SHA256=de64b7c9f109d0e83dc7d7a7e8e6eb2036254c536100a5866a58e5898b3b36b4

ENV JSCIPOPT_GIT_URL=https://github.com/scipopt/JSCIPOpt.git
ENV JSCIPOPT_GIT_COMMIT=c1fff4100f61be85b1612b22744bde3bc5514e58
ENV JSCIPOPT_DIR=/opt/JSCIPOpt

# Gurobi System variables

ENV GUROBI_HOME=/opt/gurobi/linux
ENV PATH="${GUROBI_HOME}/bin:${PATH}"
ENV LD_LIBRARY_PATH="${GUROBI_HOME}/lib:${LD_LIBRARY_PATH}"
ENV GRB_LICENSE_FILE="/opt/gurobi_lic/gurobi.lic"
ENV CLASSPATH="${GUROBI_HOME}/lib/gurobi.jar:${CLASSPATH}"

# Avoid interactive prompts

ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

# System packages

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        cmake \
        vim \
        nano \
        openjdk-11-jdk \
        ant \
        maven \
        graphviz \
        python3 \
        python3-pip \
        python3-dev \
        imagemagick \
        ca-certificates \
        wget \
        git \
        glpk-utils \
        bash-completion \
    && rm -rf /var/lib/apt/lists/*

# SCIP via .deb (Github)

WORKDIR /tmp
RUN wget -q "${SCIP_DEB_URL}" && \
    echo "${SCIP_DEB_SHA256} *${SCIP_DEB_FILENAME}" | sha256sum -c && \
    apt-get update && \
    apt-get install -y "./${SCIP_DEB_FILENAME}" && \
    rm "${SCIP_DEB_FILENAME}" && \
    rm -rf /var/lib/apt/lists/*

# JSCIPOpt via cmake

RUN git clone "${JSCIPOPT_GIT_URL}" JSCIPOpt && \
    cd JSCIPOpt && \
    git checkout "${JSCIPOPT_GIT_COMMIT}" && \
    mkdir build && cd build && \
    cmake -DCMAKE_BUILD_TYPE=Release .. && \
    make && \
    mkdir -p "${JSCIPOPT_DIR}/lib" "${JSCIPOPT_DIR}/share/java" && \
    install Release/*.so "${JSCIPOPT_DIR}/lib" && \
    install Release/scip.jar "${JSCIPOPT_DIR}/share/java" && \
    cd /tmp && rm -rf JSCIPOpt

# register shared library

RUN echo "${JSCIPOPT_DIR}/lib" > /etc/ld.so.conf.d/jscipopt.conf && ldconfig

# JSCIPOpt System variables

ENV LD_LIBRARY_PATH="${JSCIPOPT_DIR}/lib:${LD_LIBRARY_PATH}"
ENV CLASSPATH="${JSCIPOPT_DIR}/share/java/scip.jar:${CLASSPATH}"

# install python packages

RUN python3 -m pip install --no-cache-dir --break-system-packages \
    gurobipy \
    numpy \
    networkx \
    pulp \
    pandas \
    pyproj \
    matplotlib \
    seaborn \
    scikit-learn \
    sphinx \
    sphinx-rtd-theme \
    weightedstats \
    holoviews

# Ensure "python" points to python3

RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1

# Bash completion

RUN echo "if [ -f /etc/bash_completion ]; then . /etc/bash_completion; fi" >> /etc/bash.bashrc

# Copy LinTim

COPY . /workspace/lintim/

# Working directory

WORKDIR /workspace/lintim

# Default command

CMD ["/bin/bash"]
FROM ubuntu:22.04

# install java, ant, python3
RUN apt update && apt install -y openjdk-11-jdk && apt install -y ant && apt install -y python3 && apt install -y fish

# install benchexec
RUN apt install -y software-properties-common && add-apt-repository ppa:sosy-lab/benchmarking && apt install -y benchexec

# add user m.
RUN useradd -m -d /home/m -s /usr/bin/fish -p jkl m && apt install sudo && usermod -aG sudo m && echo "m ALL=(ALL:ALL) ALL" | tee /etc/sudoers.d/m

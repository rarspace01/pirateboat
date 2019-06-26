FROM ubuntu:latest
RUN apt-get update
RUN apt-get install -y wget curl unzip p7zip
RUN wget https://raw.githubusercontent.com/sormuras/bach/master/install-jdk.sh
RUN chmod +x install-jdk.sh;./install-jdk.sh -f 12 --target ./jdk
RUN wget https://rclone.org/install.sh
RUN chmod +x install.sh
RUN ./install.sh
#USER nobody
RUN wget https://raw.githubusercontent.com/rarspace01/pirateboat/master/runPirateboat.sh
RUN chmod +x runPirateboat.sh
ENV PATH="${PATH}:./jdk/bin"
# copy config for pirateBoat & if used rlcone
COPY pirateboat.cfg pirateboat.cfg
RUN ./runPirateboat.sh
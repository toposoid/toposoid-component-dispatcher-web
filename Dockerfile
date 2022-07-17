FROM toposoid/toposoid-scala-lib:0.4-SNAPSHOT

WORKDIR /app
ARG TARGET_BRANCH

ENV DEPLOYMENT=local
ENV _JAVA_OPTIONS="-Xms512m -Xmx4g"

RUN git clone https://github.com/toposoid/toposoid-component-dispatcher-web.git \
&& cd toposoid-component-dispatcher-web \
&& git fetch origin ${TARGET_BRANCH} \
&& git checkout ${TARGET_BRANCH} \
&& sbt playUpdateSecret 1> /dev/null \
&& sbt dist  \
&& cd /app/toposoid-component-dispatcher-web/target/universal  \
&& unzip -o toposoid-component-dispatcher-web-0.4-SNAPSHOT.zip


COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]


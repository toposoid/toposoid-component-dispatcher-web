FROM toposoid/toposoid-core:0.1.1

WORKDIR /app
ARG TARGET_BRANCH

ENV DEPLOYMENT=local
ENV _JAVA_OPTIONS="-Xms2g -Xmx4g"

RUN git clone https://github.com/toposoid/toposoid-component-dispatcher-web.git \
&& cd toposoid-component-dispatcher-web \
&& git checkout -b ${TARGET_BRANCH} origin/${TARGET_BRANCH} \
&& sbt playUpdateSecret 1> /dev/null \
&& sbt dist  \
&& cd /app/toposoid-component-dispatcher-web/target/universal  \
&& unzip -o toposoid-component-dispatcher-web-0.1.1.zip


COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]


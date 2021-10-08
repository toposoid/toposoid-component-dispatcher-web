FROM toposoid/toposoid-core:0.1.0

WORKDIR /app

ENV DEPLOYMENT=local
ENV _JAVA_OPTIONS="-Xms2g -Xmx4g"

RUN git clone https://github.com/toposoid/toposoid-component-dispatcher-web.git \
&& cd toposoid-component-dispatcher-web \
&& sbt playUpdateSecret 1> /dev/null \
&& sbt dist  \
&& cd /app/toposoid-component-dispatcher-web/target/universal  \
&& unzip -o toposoid-component-dispatcher-web-0.1.0.zip


COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]


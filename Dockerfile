# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM openjdk:11-jdk-oraclelinux7 as builder

RUN mkdir /code
WORKDIR /code

ENV GRADLE_OPTS -Dorg.gradle.daemon=false -Dorg.gradle.project.profile=prod

COPY ./gradle/wrapper/ /code/gradle/wrapper
COPY ./gradle/profile.prod.gradle /code/gradle/
COPY ./build.gradle ./gradle.properties ./gradlew ./settings.gradle /code/

RUN ./gradlew downloadDependencies

COPY ./src/ /code/src

RUN ./gradlew -Dkotlin.compiler.execution.strategy="in-process" -Dorg.gradle.parallel=false -Pkotlin.incremental=false distTar \
    && cd build/distributions \
    && tar xf *.tar \
    && rm *.tar radar-gateway-*/lib/radar-gateway-*.jar

FROM openjdk:11-jdk-oraclelinux7

MAINTAINER @blootsvoets

LABEL description="RADAR-base Gateway docker container"

COPY --from=builder /code/build/distributions/radar-gateway-*/bin/* /usr/bin/
COPY --from=builder /code/build/distributions/radar-gateway-*/lib/* /usr/lib/
COPY --from=builder /code/build/libs/radar-gateway-*.jar /usr/lib/

EXPOSE 8090

CMD ["radar-gateway"]

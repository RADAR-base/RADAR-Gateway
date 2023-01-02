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

FROM --platform=$BUILDPLATFORM gradle:7.6-jdk17 as builder

RUN mkdir /code
WORKDIR /code
ENV GRADLE_USER_HOME=/code/.gradlecache \
   GRADLE_OPTS="-Djdk.lang.Process.launchMechanism=vfork -Dorg.gradle.vfs.watch=false"

COPY build.gradle.kts settings.gradle.kts gradle.properties /code/

RUN gradle downloadDependencies copyDependencies startScripts

COPY src/ /code/src

RUN gradle jar

FROM eclipse-temurin:17-jre

MAINTAINER @blootsvoets

LABEL description="RADAR-base Gateway docker container"

# Override JAVA_OPTS to set heap parameters, for example
ENV JAVA_OPTS="" \
    RADAR_GATEWAY_OPTS=""

COPY --from=builder /code/build/scripts/* /usr/bin/
COPY --from=builder /code/build/third-party/* /usr/lib/
COPY --from=builder /code/build/libs/*.jar /usr/lib/

USER 101

EXPOSE 8090

CMD ["radar-gateway"]

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


FROM tomcat:8.0.37

MAINTAINER @nivemaham

LABEL description="RADAR-CNS Gateway docker container"

# Install Rest API
RUN echo && echo "==> Installing Components" \
    # Create deployment directory
    && echo "==> Creating RADAR-CNS/RADAR-Gatewat deployment directory" \
    && cd /usr/local && mkdir RADAR-Gateway && cd /usr/local/RADAR-Gateway \
    # Deploy the war
    && echo "==> Deploying the WAR"

COPY ./build/libs/radar-gateway.war /usr/local/tomcat/webapps/radar-gateway.war
    # Remove repository
RUN echo  && echo "==> Cleaning up" \
    && cd /usr/local && rm -R /usr/local/RADAR-Gateway \
    && echo

EXPOSE 8080

CMD ["catalina.sh", "run"]
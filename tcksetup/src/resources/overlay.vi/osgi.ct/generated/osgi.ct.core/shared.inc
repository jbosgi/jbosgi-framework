# Copyright (c) OSGi Alliance (2012). All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Workspace information
-runpath = \
    ../../../licensed/repo/org.jboss.osgi.framework/org.jboss.osgi.framework-3.0.0.jar;version=file, \
    ../../../licensed/repo/org.jboss.logging/jboss-logmanager.jar;version=file, \
    jar/com.springsource.junit-3.8.2.jar;version=file;export="junit.framework;version=3.8" 

-runtrace = true

-runvm="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
#-runvm="-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=n"

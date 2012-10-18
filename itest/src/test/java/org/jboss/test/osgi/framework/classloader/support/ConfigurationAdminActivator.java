/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.test.osgi.framework.classloader.support;

import java.io.IOException;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class ConfigurationAdminActivator implements BundleActivator {

    public void start(BundleContext context) {
        context.registerService(ConfigurationAdmin.class.getName(), new ConfigurationAdminImpl(), null);
    }

    public void stop(BundleContext context) {
    }
    
    static class ConfigurationAdminImpl implements ConfigurationAdmin {

        @Override
        public Configuration createFactoryConfiguration(String factoryPid) throws IOException {
            return null;
        }

        @Override
        public Configuration createFactoryConfiguration(String factoryPid, String location) throws IOException {
            return null;
        }

        @Override
        public Configuration getConfiguration(String pid, String location) throws IOException {
            return null;
        }

        @Override
        public Configuration getConfiguration(String pid) throws IOException {
            return null;
        }

        @Override
        public Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException {
            return null;
        }
    }
}
/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.util.Map;

import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * An impementation of an OSGi FrameworkFactory
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Aug-2009
 */
public class FrameworkFactoryImpl implements FrameworkFactory {

    // Main entry point used by AggregatedFrameworkLaunchTestCase
    public static void main(String[] args) throws Exception {
        FrameworkFactoryImpl factory = new FrameworkFactoryImpl();
        Framework framework = factory.newFramework(null);
        framework.start();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Framework newFramework(Map props) {
        FrameworkBuilder builder = new FrameworkBuilder(props);
        return builder.createFramework();
    }
}
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
package org.jboss.test.osgi.framework.simple;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Enumeration;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Test whether we can load a service through bundles
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Jan-2011
 */
public class SimpleServiceLoaderTestCase extends OSGiFrameworkTest {

    @Test
    public void testServiceFromArquillianBundle() throws Exception {
        Bundle bundle = installBundle(getTestArchivePath("bundles/arquillian-osgi-bundle.jar"));
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());

            assertLoadClass(bundle, "org.jboss.arquillian.spi.TestRunner");
            assertBundleState(Bundle.RESOLVED, bundle.getState());

            Enumeration<?> resources = bundle.getResources("META-INF/services/org.jboss.arquillian.spi.TestRunner");
            assertNotNull("URL not null", resources.nextElement());
            assertFalse("No more URLs", resources.hasMoreElements());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testServiceFromSystemBundle() throws Exception {
        Bundle bundle = getSystemContext().getBundle();
        assertLoadClassFail(bundle, "org.jboss.arquillian.spi.TestRunner");

        Enumeration<?> resources = bundle.getResources("META-INF/services/org.jboss.arquillian.spi.TestRunner");
        assertFalse("No more URLs", resources.hasMoreElements());
    }
}
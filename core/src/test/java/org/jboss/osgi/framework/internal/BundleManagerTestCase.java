package org.jboss.osgi.framework.internal;
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


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Version;

import java.util.Properties;

import static org.jboss.osgi.framework.internal.BundleManagerImpl.getOSVersionInOSGiFormat;

/**
 * Unit tests for the FrameworkState class.
 *
 * @author David Bosschaert
 * @author thomas.diesler@jboss.com
 */
public class BundleManagerTestCase {
    Properties savedProps;

    @Before
    public void setUp() {
        savedProps = new Properties();
        savedProps.putAll(System.getProperties());
    }

    @After
    public void tearDown() {
        System.setProperties(savedProps);
    }

    @Test
    public void testOSVersionIsValidOSGiVersion() throws Exception {
        Version.parseVersion(getOSVersionInOSGiFormat());
    }

    @Test
    public void testVersion1() throws Exception {
        System.setProperty("os.version", "1");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("1.0.0", v.toString());
    }

    @Test
    public void testVersion2() throws Exception {
        System.setProperty("os.version", "1.2.3.beta-4");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("1.2.3.beta-4", v.toString());
    }

    @Test
    public void testVersion3() throws Exception {
        System.setProperty("os.version", "1.2.3.4.5.beta6");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("1.2.3.4", v.toString());
    }

    @Test
    public void testVersion4() throws Exception {
        System.setProperty("os.version", "1.aaa.3.4.5.beta6");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("1.0.0", v.toString());
    }

    @Test
    public void testVersion5() throws Exception {
        System.setProperty("os.version", "burp");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("0.0.0", v.toString());
    }

    @Test
    public void testVersion6() throws Exception {
        System.setProperty("os.version", "");
        Version v = Version.parseVersion(getOSVersionInOSGiFormat());
        Assert.assertEquals("0.0.0", v.toString());
    }
}

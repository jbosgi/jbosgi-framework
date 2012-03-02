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
package org.jboss.test.osgi.framework;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.jboss.osgi.metadata.VersionRange;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Test versions with suffix
 * 
 * @author thomas.diesler@jboss.com
 * @since 02-Mar-2012
 */
public class VersionRangeTestCase {

    @Test
    public void testBundleLifecycle() throws Exception {
        
        Version v200 = Version.parseVersion("2.0.0");
        Version v200Alpha1 = Version.parseVersion("2.0.0.Alpha1");
        Version v200Alpha2 = Version.parseVersion("2.0.0.Alpha2");
        Version v200Beta1 = Version.parseVersion("2.0.0.Beta1");
        Version v200Beta2 = Version.parseVersion("2.0.0.Beta2");
        Version v200CR1 = Version.parseVersion("2.0.0.CR1");
        Version v200CR2 = Version.parseVersion("2.0.0.CR2");
        Version v200GA = Version.parseVersion("2.0.0.GA");
        Version v200Final = Version.parseVersion("2.0.0.Final");
        Version v200SP1 = Version.parseVersion("2.0.0.SP1");
        Version v200SP2 = Version.parseVersion("2.0.0.SP2");
        
        assertTrue(v200SP2.compareTo(v200SP1) > 0);
        assertTrue(v200SP1.compareTo(v200GA) > 0);
        assertTrue(v200GA.compareTo(v200Final) > 0);
        assertTrue(v200Final.compareTo(v200CR2) > 0);
        assertTrue(v200CR2.compareTo(v200CR1) > 0);
        assertTrue(v200CR1.compareTo(v200Beta2) > 0);
        assertTrue(v200Beta2.compareTo(v200Beta1) > 0);
        assertTrue(v200Beta1.compareTo(v200Alpha2) > 0);
        assertTrue(v200Alpha2.compareTo(v200Alpha1) > 0);
        
        // 2.0.0.Alpha1 > 2.0.0
        Assert.assertTrue(v200Alpha1.compareTo(v200) > 0);
        
        VersionRange rA = VersionRange.parse("[1.0,3.0)");
        assertTrue(rA.isInRange(v200Final));
        assertTrue(rA.isInRange(v200GA));
        assertTrue(rA.isInRange(v200CR1));
        assertTrue(rA.isInRange(v200Beta1));
        assertTrue(rA.isInRange(v200Alpha1));
        assertTrue(rA.isInRange(v200));
        
        VersionRange rB = VersionRange.parse("[2.0,3.0)");
        assertTrue(rB.isInRange(v200Final));
        assertTrue(rB.isInRange(v200GA));
        assertTrue(rB.isInRange(v200CR1));
        assertTrue(rB.isInRange(v200Beta1));
        assertTrue(rB.isInRange(v200Alpha1));
        assertTrue(rB.isInRange(v200));

        VersionRange rC = VersionRange.parse("[1.0,2.0)");
        assertFalse(rC.isInRange(v200Final));
        assertFalse(rC.isInRange(v200GA));
        assertFalse(rC.isInRange(v200CR1));
        assertFalse(rC.isInRange(v200Beta1));
        assertFalse(rC.isInRange(v200Alpha1));
        assertFalse(rC.isInRange(v200));
    }
}
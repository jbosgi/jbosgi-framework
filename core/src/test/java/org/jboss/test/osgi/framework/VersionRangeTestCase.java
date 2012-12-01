package org.jboss.test.osgi.framework;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * Test versions with suffix
 *
 * @author thomas.diesler@jboss.com
 * @since 02-Mar-2012
 */
public class VersionRangeTestCase {

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

    @Test
    public void testVersionOrdering() throws Exception {

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
    }

    @Test
    public void testVersionRange() throws Exception {

        VersionRange rA = new VersionRange("[1.0,3.0)");
        assertTrue(rA.includes(v200Final));
        assertTrue(rA.includes(v200GA));
        assertTrue(rA.includes(v200CR1));
        assertTrue(rA.includes(v200Beta1));
        assertTrue(rA.includes(v200Alpha1));
        assertTrue(rA.includes(v200));

        VersionRange rB = new VersionRange("[2.0,3.0)");
        assertTrue(rB.includes(v200Final));
        assertTrue(rB.includes(v200GA));
        assertTrue(rB.includes(v200CR1));
        assertTrue(rB.includes(v200Beta1));
        assertTrue(rB.includes(v200Alpha1));
        assertTrue(rB.includes(v200));

        VersionRange rC = new VersionRange("[1.0,2.0)");
        assertFalse(rC.includes(v200Final));
        assertFalse(rC.includes(v200GA));
        assertFalse(rC.includes(v200CR1));
        assertFalse(rC.includes(v200Beta1));
        assertFalse(rC.includes(v200Alpha1));
        assertFalse(rC.includes(v200));

        VersionRange rD = new VersionRange("[2.0,2.0]");
        assertTrue(rD.includes(v200));

        // Versions with qualifier not in range
        assertFalse(rD.includes(v200Final));
        assertFalse(rD.includes(v200GA));
        assertFalse(rD.includes(v200CR1));
        assertFalse(rD.includes(v200Beta1));
        assertFalse(rD.includes(v200Alpha1));
    }
}
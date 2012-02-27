/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.framework.bundle;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * BundleEntriesTest.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class BundleEntriesTestCase extends OSGiFrameworkTest {

    @Test
    public void testEntriesNotInstalled() throws Exception {
        Bundle bundle = installBundle(assembleArchive("entries-simple", "/bundles/entries/entries-simple"));
        bundle.uninstall();
        try {
            bundle.getEntry("root.xml");
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }
        try {
            bundle.findEntries("", "root.xml", false);
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }
    }

    @Test
    public void testFindEntriesNoPath() throws Exception {
        Bundle bundle = installBundle(assembleArchive("entries-simple", "/bundles/entries/entries-simple"));
        try {
            bundle.findEntries(null, "root.xml", false);
            fail("Should not be here!");
        } catch (IllegalArgumentException t) {
            // expected
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testEntries() throws Exception {
        Bundle bundle = installBundle(assembleArchive("entries-simple", "/bundles/entries/entries-simple"));
        try {
            assertEntry(bundle, "");
            assertNoEntries(bundle, "", "", false);
            assertNoEntries(bundle, "", "", true);
            assertEntryPaths("", bundle, "root.xml", "root-no-suffix", "entry1.xml", "META-INF/", "org/");

            assertNoEntry(bundle, "DoesNotExist");
            assertNoEntries(bundle, "", "DoesNotExist", false);
            assertNoEntries(bundle, "", "DoesNotExist", true);
            assertNoEntryPaths(bundle, "DoesNotExist");

            assertEntry(bundle, "root-no-suffix");
            assertEntries(bundle, "", "root-no-suffix", false, "root-no-suffix");
            assertEntries(bundle, "", "root-no-suffix", true, "root-no-suffix");
            assertNoEntryPaths(bundle, "root-no-suffix");

            assertEntry(bundle, "root.xml");
            assertEntries(bundle, "", "root.xml", false, "root.xml");
            assertEntries(bundle, "", "root.xml", true, "root.xml");
            assertNoEntryPaths(bundle, "root.xml");

            assertEntry(bundle, "entry1.xml");
            assertEntries(bundle, "", "entry1.xml", false, "entry1.xml");
            assertEntries(bundle, "", "entry1.xml", true, "entry1.xml", "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");
            assertNoEntryPaths(bundle, "entry1.xml");

            assertEntry(bundle, "META-INF");
            assertEntries(bundle, "", "META-INF", false, "META-INF/");
            assertEntries(bundle, "", "META-INF", true, "META-INF/");
            assertEntryPaths("META-INF", bundle, "META-INF/MANIFEST.MF");

            assertNoEntry(bundle, "META-INF/DoesNotExist");
            assertNoEntries(bundle, "META-INF", "DoesNotExist", false);
            assertNoEntries(bundle, "META-INF", "DoesNotExist", true);
            assertNoEntryPaths(bundle, "META-INF/DoesNotExist");

            assertEntry(bundle, "META-INF/MANIFEST.MF");
            assertEntries(bundle, "META-INF", "MANIFEST.MF", false, "META-INF/MANIFEST.MF");
            assertEntries(bundle, "META-INF", "MANIFEST.MF", true, "META-INF/MANIFEST.MF");
            assertNoEntryPaths(bundle, "META-INF/MANIFEST.MF");

            assertEntry(bundle, "org");
            assertEntries(bundle, "", "org", false, "org/");
            assertEntries(bundle, "", "org", true, "org/");
            assertEntryPaths("org", bundle, "org/jboss/");

            assertNoEntry(bundle, "org/DoesNotExist");
            assertNoEntries(bundle, "org", "DoesNotExist", false);
            assertNoEntries(bundle, "org", "DoesNotExist", true);
            assertNoEntryPaths(bundle, "org/DoesNotExist");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries");
            assertEntries(bundle, "", "org/jboss/test/osgi/bundle/entries", false);
            assertEntries(bundle, "", "org/jboss/test/osgi/bundle/entries", true);
            assertEntryPaths("org/jboss/test/osgi/bundle/entries", bundle,
                    "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/");

            assertNoEntry(bundle, "org/jboss/test/osgi/bundle/DoesNotExist");
            assertNoEntries(bundle, "org/jboss/test/osgi/bundle", "DoesNotExist", false);
            assertNoEntries(bundle, "org/jboss/test/osgi/bundle", "DoesNotExist", true);
            assertNoEntryPaths(bundle, "org/jboss/test/osgi/bundle/DoesNotExist");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/notxml.suffix");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "notxml.suffix", false, "org/jboss/test/osgi/bundle/entries/notxml.suffix");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "notxml.suffix", true, "org/jboss/test/osgi/bundle/entries/notxml.suffix");
            assertNoEntryPaths(bundle, "org/jboss/test/osgi/bundle/entries/notxml.suffix");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/entry1.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry1.xml", false, "org/jboss/test/osgi/bundle/entries/entry1.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry1.xml", true, "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");
            assertNoEntryPaths(bundle,
                    "org/jboss/test/osgi/bundle/entries/entry1.xml");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry2.xml", false, "org/jboss/test/osgi/bundle/entries/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry2.xml", true, "org/jboss/test/osgi/bundle/entries/entry2.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");
            assertNoEntryPaths(bundle,
                    "org/jboss/test/osgi/bundle/entries/entry2.xml");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/sub");
            assertEntries(bundle, "", "org/jboss/test/osgi/bundle/entries/sub", false);
            assertEntries(bundle, "", "org/jboss/test/osgi/bundle/entries/sub", true);
            assertEntryPaths("org/jboss/test/osgi/bundle/entries/sub", bundle,
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml", "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertNoEntry(bundle, "org/jboss/test/osgi/bundle/DoesNotExist/sub");
            assertNoEntries(bundle, "org/jboss/test/osgi/bundle/sub", "DoesNotExist", false);
            assertNoEntries(bundle, "org/jboss/test/osgi/bundle/sub", "DoesNotExist", true);
            assertNoEntryPaths(bundle, "org/jboss/test/osgi/bundle/DoesNotExist/sub");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries/sub", "entry1.xml", false, "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries/sub", "entry1.xml", true, "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");
            assertNoEntryPaths(bundle, "org/jboss/test/osgi/bundle/entries/sub/entry1.xml");

            assertEntry(bundle, "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries/sub", "entry2.xml", false, "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries/sub", "entry2.xml", true, "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");
            assertNoEntryPaths(bundle, "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "", "*", false, "root.xml", "root-no-suffix", "entry1.xml", "META-INF/", "org/");
            assertEntries(bundle, "", "*", true, "root.xml", "root-no-suffix", "entry1.xml", "META-INF/", "META-INF/MANIFEST.MF",
                    "org/", "org/jboss/", "org/jboss/test/", "org/jboss/test/osgi/", "org/jboss/test/osgi/bundle/",
                    "org/jboss/test/osgi/bundle/entries/", "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/sub/",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml", "org/jboss/test/osgi/bundle/entries/sub/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml", "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "", null, false, "root.xml", "root-no-suffix", "entry1.xml", "META-INF/", "org/");
            assertEntries(bundle, "", null, true, "root.xml", "root-no-suffix", "entry1.xml", "META-INF/", "META-INF/MANIFEST.MF",
                    "org/", "org/jboss/", "org/jboss/test/", "org/jboss/test/osgi/", "org/jboss/test/osgi/bundle/",
                    "org/jboss/test/osgi/bundle/entries/", "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/sub/",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml", "org/jboss/test/osgi/bundle/entries/sub/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml", "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "", "root*", false, "root-no-suffix", "root.xml");
            assertEntries(bundle, "", "root*", true, "root-no-suffix", "root.xml");

            assertEntries(bundle, "", "entry*", false, "entry1.xml");
            assertEntries(bundle, "", "entry*", true, "entry1.xml", "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml", "org/jboss/test/osgi/bundle/entries/entry2.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry*", false, "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "entry*", true, "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml", "org/jboss/test/osgi/bundle/entries/entry2.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "", "*.xml", false, "root.xml", "entry1.xml");
            assertEntries(bundle, "", "*.xml", true, "root.xml", "entry1.xml", "org/jboss/test/osgi/bundle/entries/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry1.xml", "org/jboss/test/osgi/bundle/entries/entry2.xml",
                    "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "", "*xml*", false, "root.xml", "entry1.xml");
            assertEntries(bundle, "", "*xml*", true, "root.xml", "entry1.xml", "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml", "org/jboss/test/osgi/bundle/entries/sub/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml", "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");

            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "*xml*", false, "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml", "org/jboss/test/osgi/bundle/entries/entry2.xml");
            assertEntries(bundle, "org/jboss/test/osgi/bundle/entries", "*xml*", true, "org/jboss/test/osgi/bundle/entries/notxml.suffix",
                    "org/jboss/test/osgi/bundle/entries/entry1.xml", "org/jboss/test/osgi/bundle/entries/sub/entry1.xml",
                    "org/jboss/test/osgi/bundle/entries/entry2.xml", "org/jboss/test/osgi/bundle/entries/sub/entry2.xml");
        } finally {
            bundle.uninstall();
        }
    }

    private void assertEntry(Bundle bundle, String path) throws Exception {
        URL actual = bundle.getEntry(path);
        assertNotNull("Entry expected for: " + path, actual);
    }

    protected void assertNoEntry(Bundle bundle, String path) throws Exception {
        URL actual = bundle.getEntry(path);
        assertNull("Did not expect entry: " + actual + " for path: " + path, actual);
    }

    @SuppressWarnings("unchecked")
    protected void assertEntries(Bundle bundle, String path, String filePattern, boolean recurse, String... entries) throws Exception {
        Set<URI> actual = new HashSet<URI>();
        Enumeration<URL> enumeration = bundle.findEntries(path, filePattern, recurse);
        while (enumeration != null && enumeration.hasMoreElements())
            actual.add(enumeration.nextElement().toURI());

        URL baseurl = bundle.getEntry("/");
        Set<URI> expected = new HashSet<URI>();
        for (String entry : entries)
            expected.add(new URI(baseurl + entry));

        assertEquals(expected, actual);
    }

    protected void assertNoEntries(Bundle bundle, String path, String filePattern, boolean recurse) throws Exception {
        assertEntries(bundle, path, filePattern, recurse);
    }

    protected void assertEntryPaths(Bundle bundle, String path) throws Exception {
        Set<String> expected = Collections.singleton(path);

        assertEntryPaths(bundle, path, expected);
        assertEntryPaths(bundle, "/" + path, expected);
    }

    protected void assertEntryPaths(String path, Bundle bundle, String... entries) throws Exception {
        Set<String> expected = new HashSet<String>();
        expected.addAll(Arrays.asList(entries));

        assertEntryPaths(bundle, path, expected);
        assertEntryPaths(bundle, "/" + path, expected);
    }

    @SuppressWarnings("unchecked")
    protected void assertEntryPaths(Bundle bundle, String path, Set<String> expected) throws Exception {
        Set<String> actual = new HashSet<String>();
        Enumeration<String> enumeration = bundle.getEntryPaths(path);
        while (enumeration != null && enumeration.hasMoreElements())
            actual.add(enumeration.nextElement());

        assertEquals(expected, actual);
    }

    @SuppressWarnings("unchecked")
    protected void assertNoEntryPaths(Bundle bundle, String path) throws Exception {
        Enumeration<String> enumeration = bundle.getEntryPaths(path);
        if (enumeration != null) {
            Set<String> actual = new HashSet<String>();
            while (enumeration.hasMoreElements())
                actual.add(enumeration.nextElement());
            fail("For path " + path + " did not expect entry paths: " + actual);
        }
    }
}

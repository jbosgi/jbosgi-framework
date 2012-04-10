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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.osgi.service.url.AbstractURLStreamHandlerService;

/**
 * A handler for the 'bundle' protocol.
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jan-2011
 */
public class BundleProtocolHandler extends AbstractURLStreamHandlerService {

    public static final String PROTOCOL_NAME = "bundle";

    private final BundleManager bundleManager;

    BundleProtocolHandler(BundleManager bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    public URLConnection openConnection(URL url) throws IOException {
        RevisionContent revContent = RevisionContent.findRevisionContent(bundleManager, url.getHost());
        if (revContent == null)
            throw MESSAGES.ioCannotObtainRevisionContent(url);
        URL entry = revContent.getEntry(url.getPath());
        if (entry == null)
            throw MESSAGES.ioCannotObtainContent(url);
        return entry.openConnection();
    }

    // [TODO] overwrite hashCode for to prevent host address resolution
    // when offline the BundleEntriesTestCase is slow because of this 
}

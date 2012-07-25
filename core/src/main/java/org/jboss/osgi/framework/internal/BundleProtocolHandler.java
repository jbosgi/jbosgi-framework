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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
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

    private final BundleManagerPlugin bundleManager;

    BundleProtocolHandler(BundleManagerPlugin bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    public URLConnection openConnection(URL url) throws IOException {
        LOGGER.tracef("openConnection: %s", url);
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

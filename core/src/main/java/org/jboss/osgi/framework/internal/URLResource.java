/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.framework.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.jboss.modules.Resource;

/**
 * A {@link Resource} that is backed by an URL.
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jan-2011
 */
final class URLResource implements Resource {

    private final URL url;

    URLResource(final URL url) {
        this.url = url;
    }

    public String getName() {
        return url.getPath();
    }

    public URL getURL() {
        return url;
    }

    public InputStream openStream() throws IOException {
        return url.openStream();
    }

    public long getSize() {
        return 0L;
    }
}
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
package org.jboss.osgi.framework.internal;

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.osgi.framework.ServiceReference;

/**
 * There can only ever be one ContentHandlerFactory active in the system and it can only be set once, using
 * {@link URLConnection#setContentHandlerFactory()}. This delegate makes it possible to replace this factory after it has been
 * set, which is useful for testing purposes.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
final class OSGiContentHandlerFactoryDelegate implements ContentHandlerFactory {

    private ContentHandlerFactory delegate;
    private Map<String, List<ServiceReference<ContentHandler>>> contentHandlers = new HashMap<String, List<ServiceReference<ContentHandler>>>();

    void setDelegateFactory(OSGiContentHandlerFactory factory) {
        delegate = factory;
    }

    void addHandler(String mimeType, ServiceReference<ContentHandler> reference) {
        synchronized (contentHandlers) {
            List<ServiceReference<ContentHandler>> list = contentHandlers.get(mimeType);
            if (list == null) {
                list = new ArrayList<ServiceReference<ContentHandler>>();
                contentHandlers.put(mimeType, list);
            }
            list.add(reference);
            Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
        }
    }

    List<ServiceReference<ContentHandler>> getContentHandlers(String mimetype) {
        synchronized (contentHandlers) {
            return contentHandlers.get(mimetype);
        }
    }

    void removeHandler(ServiceReference<ContentHandler> reference) {
        synchronized (contentHandlers) {
            for (List<ServiceReference<ContentHandler>> list : contentHandlers.values()) {
                for (Iterator<ServiceReference<ContentHandler>> it = list.iterator(); it.hasNext();) {
                    if (it.next().equals(reference)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    void clearHandlers() {
        synchronized (contentHandlers) {
            for (List<ServiceReference<ContentHandler>> list : contentHandlers.values()) {
                list.clear();
            }
        }
    }

    @Override
    public ContentHandler createContentHandler(String mimetype) {
        return delegate != null ? delegate.createContentHandler(mimetype) : null;
    }
}

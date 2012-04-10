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

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private ConcurrentMap<String, List<ServiceReference>> contentHandlers = new ConcurrentHashMap<String, List<ServiceReference>>();

    void setDelegateFactory(OSGiContentHandlerFactory factory) {
        delegate = factory;
    }

    void addHandler(String mimeType, ServiceReference reference) {
        synchronized (contentHandlers) {
            contentHandlers.putIfAbsent(mimeType, new ArrayList<ServiceReference>());
            List<ServiceReference> list = contentHandlers.get(mimeType);
            synchronized (list) {
                list.add(reference);
                Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
            }
        }
    }

    List<ServiceReference> getContentHandlers(String mimetype) {
        synchronized (contentHandlers) {
            return contentHandlers.get(mimetype);
        }
    }

    void removeHandler(ServiceReference reference) {
        synchronized (contentHandlers) {
            for (List<ServiceReference> list : contentHandlers.values()) {
                for (Iterator<ServiceReference> it = list.iterator(); it.hasNext();) {
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
            for (List<ServiceReference> list : contentHandlers.values()) {
                list.clear();
            }
        }
    }

    @Override
    public ContentHandler createContentHandler(String mimetype) {
        return delegate != null ? delegate.createContentHandler(mimetype) : null;
    }
}

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

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.osgi.framework.ServiceReference;

/**
 * There can only ever be one URLStreamHandlerFactory active in the system and it can only be set once, using
 * {@link URL#setURLStreamHandlerFactory()}.
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
final class OSGiStreamHandlerFactoryDelegate implements URLStreamHandlerFactory {

    private URLStreamHandlerFactory delegate;
    private ConcurrentMap<String, List<ServiceReference>> streamHandlers = new ConcurrentHashMap<String, List<ServiceReference>>();

    void setDelegateFactory(URLStreamHandlerFactory factory) {
        delegate = factory;
    }

    void addHandler(String protocol, ServiceReference reference) {
        synchronized (streamHandlers) {
            streamHandlers.putIfAbsent(protocol, new ArrayList<ServiceReference>());
            List<ServiceReference> list = streamHandlers.get(protocol);
            synchronized (list) {
                list.add(reference);
                Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
            }
        }
    }

    List<ServiceReference> getStreamHandlers(String protocol) {
        synchronized (streamHandlers) {
            return streamHandlers.get(protocol);
        }
    }

    void removeHandler(ServiceReference reference) {
        synchronized (streamHandlers) {
            for (List<ServiceReference> list : streamHandlers.values()) {
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
        synchronized (streamHandlers) {
            for (List<ServiceReference> list : streamHandlers.values()) {
                list.clear();
            }
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return delegate != null ? delegate.createURLStreamHandler(protocol) : null;
    }
}

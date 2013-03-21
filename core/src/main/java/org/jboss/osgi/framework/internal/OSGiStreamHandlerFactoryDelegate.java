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

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    private Map<String, List<ServiceReference<URLStreamHandler>>> streamHandlers = new HashMap<String, List<ServiceReference<URLStreamHandler>>>();

    void setDelegateFactory(URLStreamHandlerFactory factory) {
        delegate = factory;
    }

    void addHandler(String protocol, ServiceReference<URLStreamHandler> reference) {
        synchronized (streamHandlers) {
            List<ServiceReference<URLStreamHandler>> list = streamHandlers.get(protocol);
            if (list == null) {
                list = new ArrayList<ServiceReference<URLStreamHandler>>();
                streamHandlers.put(protocol, list);
            }
            list.add(reference);
            Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
        }
    }

    List<ServiceReference<URLStreamHandler>> getStreamHandlers(String protocol) {
        synchronized (streamHandlers) {
            return streamHandlers.get(protocol);
        }
    }

    void removeHandler(ServiceReference<URLStreamHandler> reference) {
        synchronized (streamHandlers) {
            for (List<ServiceReference<URLStreamHandler>> list : streamHandlers.values()) {
                for (Iterator<ServiceReference<URLStreamHandler>> it = list.iterator(); it.hasNext();) {
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
            for (List<ServiceReference<URLStreamHandler>> list : streamHandlers.values()) {
                list.clear();
            }
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return delegate != null ? delegate.createURLStreamHandler(protocol) : null;
    }
}

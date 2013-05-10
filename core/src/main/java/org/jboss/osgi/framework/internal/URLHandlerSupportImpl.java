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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ContentHandler;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.jboss.osgi.framework.spi.URLHandlerSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This plugin provides OSGi URL handler support as per the specification.
 * The interface is through the java.net.URL class and the OSGi Service Registry.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
final class URLHandlerSupportImpl implements URLHandlerSupport {

    private final BundleManagerPlugin bundleManager;
    private ServiceTracker<URLStreamHandlerService, URLStreamHandlerService> streamServiceTracker;
    private ServiceTracker<ContentHandler, ContentHandler> contentServiceTracker;
    private ServiceRegistration<URLStreamHandlerService> registration;

    private static OSGiContentHandlerFactoryDelegate contentHandlerDelegate;
    private static OSGiStreamHandlerFactoryDelegate streamHandlerDelegate;

    static void initURLHandlerSupport() {
    	if (streamHandlerDelegate == null) {
            streamHandlerDelegate = new OSGiStreamHandlerFactoryDelegate();
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        URL.setURLStreamHandlerFactory(streamHandlerDelegate);
                    } catch (Throwable th) {
                        // [MODULES-44] Provide an API that allows adding of URLStreamHandlerFactories
                        LOGGER.debugf("Unable to set the URLStreamHandlerFactory");
                    }
                    return null;
                }
            });
    	}
    	if (contentHandlerDelegate == null) {
            contentHandlerDelegate = new OSGiContentHandlerFactoryDelegate();
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        URLConnection.setContentHandlerFactory(contentHandlerDelegate);
                    } catch (Throwable th) {
                        // [MODULES-44] Provide an API that allows adding of URLStreamHandlerFactories
                        LOGGER.debugf("Unable to set the ContentHandlerFactory");
                    }
                    return null;
                }
            });
    	}
    }

    public URLHandlerSupportImpl(BundleManagerPlugin bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    public void start(BundleContext systemContext) {
        OSGiStreamHandlerFactoryService.setDelegateFactory(this);
        streamHandlerDelegate.setDelegateFactory(new OSGiStreamHandlerFactory(this));
        contentHandlerDelegate.setDelegateFactory(new OSGiContentHandlerFactory(this));
        registerStreamHandlerService();
        setupStreamHandlerTracker(systemContext);
        setupContentHandlerTracker(systemContext);
    }

    @Override
    public void stop() {
        streamHandlerDelegate.clearHandlers();
        contentHandlerDelegate.clearHandlers();
        registration.unregister();
        streamHandlerDelegate.setDelegateFactory(null);
        contentHandlerDelegate.setDelegateFactory(null);
        OSGiStreamHandlerFactoryService.setDelegateFactory(null);
    }

    private void registerStreamHandlerService() {
        // Register the 'bundle' protocol
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(URLConstants.URL_HANDLER_PROTOCOL, BundleProtocolHandler.PROTOCOL_NAME);
        BundleProtocolHandler service = new BundleProtocolHandler(bundleManager);
        BundleContext systemContext = bundleManager.getSystemBundle().getBundleContext();
        registration = systemContext.registerService(URLStreamHandlerService.class, service, props);
    }

    private void setupContentHandlerTracker(BundleContext systemContext) {
        contentServiceTracker = new ServiceTracker<ContentHandler, ContentHandler>(systemContext, ContentHandler.class, null) {

            @Override
            public ContentHandler addingService(ServiceReference<ContentHandler> reference) {
                ContentHandler service = super.addingService(reference);
                String[] mimeTypes = parseServiceProperty(reference.getProperty(URLConstants.URL_CONTENT_MIMETYPE));
                if (mimeTypes != null && service instanceof ContentHandler) {
                    LOGGER.debugf("Adding content handler '%s' for: %s", service, Arrays.asList(mimeTypes));
                    for (String mimeType : mimeTypes) {
                        contentHandlerDelegate.addHandler(mimeType, reference);
                    }
                }
                return service;
            }

            @Override
            public void modifiedService(ServiceReference<ContentHandler> reference, ContentHandler service) {
                removedService(reference, service);
                addingService(reference);
            }

            @Override
            public void removedService(ServiceReference<ContentHandler> reference, ContentHandler service) {
                super.removedService(reference, service);
                contentHandlerDelegate.removeHandler(reference);
            }
        };
        contentServiceTracker.open();
    }

    private void setupStreamHandlerTracker(BundleContext systemContext) {
        streamServiceTracker = new ServiceTracker<URLStreamHandlerService, URLStreamHandlerService>(systemContext, URLStreamHandlerService.class, null) {

            @Override
            public URLStreamHandlerService addingService(ServiceReference<URLStreamHandlerService> reference) {
                URLStreamHandlerService service = super.addingService(reference);
                String[] protocols = parseServiceProperty(reference.getProperty(URLConstants.URL_HANDLER_PROTOCOL));
                if (protocols != null && service instanceof URLStreamHandlerService) {
                    LOGGER.tracef("Adding stream handler '%s' for: %s", service, Arrays.asList(protocols));
                    for (String protocol : protocols) {
                        streamHandlerDelegate.addHandler(protocol, reference);
                    }
                }
                return service;
            }

            @Override
            public void modifiedService(ServiceReference<URLStreamHandlerService> reference, URLStreamHandlerService service) {
                removedService(reference, service);
                addingService(reference);
            }

            @Override
            public void removedService(ServiceReference<URLStreamHandlerService> reference, URLStreamHandlerService service) {
                super.removedService(reference, service);
                streamHandlerDelegate.removeHandler(reference);
            }
        };
        streamServiceTracker.open();
    }

    /**
     * Creates a new <code>URLStreamHandler</code> instance with the specified protocol.
     *
     * @see java.net.URLStreamHandler
     */
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        List<ServiceReference<URLStreamHandlerService>> refList = streamHandlerDelegate.getStreamHandlers(protocol);
        if (refList == null || refList.isEmpty())
            return null;

        return new URLStreamHandlerProxy(protocol, refList);
    }

    /**
     * Creates a new <code>ContentHandler</code> to read an object from a <code>URLStreamHandler</code>.
     *
     * @see java.net.ContentHandler
     * @see java.net.URLStreamHandler
     */
    @Override
    public ContentHandler createContentHandler(String mimetype) {
        List<ServiceReference<ContentHandler>> refList = contentHandlerDelegate.getContentHandlers(mimetype);
        if (refList == null || refList.isEmpty())
            return null;

        ServiceReference<ContentHandler> ref = refList.get(0);
        Object service = ref.getBundle().getBundleContext().getService(ref);
        if (service instanceof ContentHandler)
            return (ContentHandler) service;

        return null;
    }

    /**
     * Parses a service registration property with a value which can be of type String or String [].
     *
     * @param prop The property value.
     * @return All the values found in a String [] or null of the property doesn't comply.
     */
    private String[] parseServiceProperty(Object prop) {
        if (prop == null)
            return null;

        if (prop instanceof String)
            return new String[] { (String) prop };

        if (prop instanceof String[])
            return (String[]) prop;

        return null;
    }

    private static final class URLStreamHandlerProxy extends URLStreamHandler implements URLStreamHandlerSetter {

        // This list is maintained in the ServiceTracker that tracks the URLStreamHandlerService
        // This proxy should always use to top element (if it contains any elements).
        private final List<ServiceReference<URLStreamHandlerService>> serviceReferences;
        private final String protocol;

        public URLStreamHandlerProxy(String protocol, List<ServiceReference<URLStreamHandlerService>> refList) {
            this.protocol = protocol;
            this.serviceReferences = refList;
        }

        @Override
        public void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path, String query, String ref) {
            // Made public to implement URLStreamHandlerSetter
            super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setURL(URL u, String protocol, String host, int port, String file, String ref) {
            // Made public to implement URLStreamHandlerSetter
            super.setURL(u, protocol, host, port, file, ref);
        }

        @Override
        protected void parseURL(URL u, String spec, int start, int limit) {
            getHandlerService().parseURL(this, u, spec, start, limit);
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return getHandlerService().openConnection(u);
        }

        @Override
        protected String toExternalForm(URL u) {
            return getHandlerService().toExternalForm(u);
        }

        @Override
        protected URLConnection openConnection(URL u, Proxy p) throws IOException {
            URLStreamHandlerService handler = getHandlerService();
            try {
                Method method = handler.getClass().getDeclaredMethod("openConnection", URL.class, Proxy.class);
                return (URLConnection) method.invoke(handler, u, p);
            } catch (Throwable th) {
                if (th instanceof InvocationTargetException)
                    th = ((InvocationTargetException)th).getTargetException();
                throw MESSAGES.cannotOpenConnectionOnHandler(th, handler);
            }
        }

        @Override
        protected int getDefaultPort() {
            return getHandlerService().getDefaultPort();
        }

        @Override
        protected boolean equals(URL u1, URL u2) {
            return getHandlerService().equals(u1, u2);
        }

        @Override
        protected int hashCode(URL u) {
            return getHandlerService().hashCode(u);
        }

        @Override
        protected boolean sameFile(URL u1, URL u2) {
            return getHandlerService().sameFile(u1, u2);
        }

        @Override
        protected synchronized InetAddress getHostAddress(URL u) {
            return getHandlerService().getHostAddress(u);
        }

        @Override
        protected boolean hostsEqual(URL u1, URL u2) {
            return getHandlerService().hostsEqual(u1, u2);
        }

        private URLStreamHandlerService getHandlerService() {
            if (serviceReferences.isEmpty())
                throw MESSAGES.illegalStateNoStreamHandlersForProtocol(protocol);
            ServiceReference<URLStreamHandlerService> ref = serviceReferences.get(0);
            URLStreamHandlerService service = ref.getBundle().getBundleContext().getService(ref);
            return service;
        }
    }
}

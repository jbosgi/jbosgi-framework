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
package org.jboss.osgi.framework.plugin.internal;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.bundle.ServiceReferenceComparator;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.URLHandlerPlugin;
import org.jboss.osgi.framework.url.OSGiStreamHandlerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This plugin provide OSGi URL Handler support. It is realized by plugging into the
 * {@link org.jboss.modules.ModularURLStreamHandlerFactory} class.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
public class URLHandlerPluginImpl extends AbstractPlugin implements URLHandlerPlugin
{
   private final Logger log = Logger.getLogger(URLHandlerPluginImpl.class);

   private ServiceTracker streamServiceTracker;
   private ServiceTracker contentServiceTracker;
   private ConcurrentMap<String, List<ServiceReference>> streamHandlers = new ConcurrentHashMap<String, List<ServiceReference>>();
   private ConcurrentMap<String, List<ServiceReference>> contentHandlers = new ConcurrentHashMap<String, List<ServiceReference>>();

   private static ContentHandlerFactoryDelegate contentHandlerDelegate;
   private static OSGiStreamHandlerFactoryDelegate streamHandlerDelegate;

   public URLHandlerPluginImpl(final BundleManager bundleManager)
   {
      super(bundleManager);

      if (streamHandlerDelegate == null)
      {
         streamHandlerDelegate = new OSGiStreamHandlerFactoryDelegate();
         AccessController.doPrivileged(new PrivilegedAction<Void>()
         {
            public Void run()
            {
               try
               {
                  URL.setURLStreamHandlerFactory(streamHandlerDelegate);
               }
               catch (Throwable th)
               {
                  if (bundleManager.getIntegrationMode() == IntegrationMode.STANDALONE)
                     log.warn("Unable to set the URLStreamHandlerFactory", th);
               }
               return null;
            }
         });
      }
      streamHandlerDelegate.setDelegate(new OSGiStreamHandlerFactory(this));

      if (contentHandlerDelegate == null)
      {
         contentHandlerDelegate = new ContentHandlerFactoryDelegate();
         AccessController.doPrivileged(new PrivilegedAction<Void>()
         {
            public Void run()
            {
               try
               {
                  URLConnection.setContentHandlerFactory(contentHandlerDelegate);
               }
               catch (Throwable th)
               {
                  if (bundleManager.getIntegrationMode() == IntegrationMode.STANDALONE)
                     log.warn("Unable to set the ContentHandlerFactory", th);
               }
               return null;
            }
         });
      }
      contentHandlerDelegate.setDelegate(new FrameworkContentHandlerFactory(this));
   }

   @Override
   public void startPlugin()
   {
      BundleContext sysContext = getBundleManager().getSystemContext();
      streamServiceTracker = new ServiceTracker(sysContext, URLStreamHandlerService.class.getName(), null)
      {
         @Override
         public Object addingService(ServiceReference reference)
         {
            Object svc = super.addingService(reference);
            String[] protocols = parseServiceProperty(reference.getProperty(URLConstants.URL_HANDLER_PROTOCOL));
            if (protocols != null && svc instanceof URLStreamHandlerService)
            {
               for (String protocol : protocols)
               {
                  streamHandlers.putIfAbsent(protocol, new ArrayList<ServiceReference>());
                  List<ServiceReference> list = streamHandlers.get(protocol);
                  synchronized (list)
                  {
                     list.add(reference);
                     Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
                  }
               }
            }
            else
            {
               log.error("A non-compliant instance of " + URLStreamHandlerService.class.getName() + " has been registered for protocols: "
                     + Arrays.toString(protocols) + " - " + svc);
            }
            return svc;
         }

         @Override
         public void modifiedService(ServiceReference reference, Object service)
         {
            removedService(reference, service);
            addingService(reference);
         }

         @Override
         public void removedService(ServiceReference reference, Object service)
         {
            super.removedService(reference, service);

            for (List<ServiceReference> list : streamHandlers.values())
            {
               synchronized (list)
               {
                  for (Iterator<ServiceReference> it = list.iterator(); it.hasNext();)
                  {
                     if (it.next().equals(reference))
                     {
                        it.remove();
                        break;
                     }
                  }
               }
            }
         }
      };
      streamServiceTracker.open();

      contentServiceTracker = new ServiceTracker(sysContext, ContentHandler.class.getName(), null)
      {
         @Override
         public Object addingService(ServiceReference reference)
         {
            Object svc = super.addingService(reference);
            String[] mimeTypes = parseServiceProperty(reference.getProperty(URLConstants.URL_CONTENT_MIMETYPE));
            if (mimeTypes != null && svc instanceof ContentHandler)
            {
               for (String mimeType : mimeTypes)
               {
                  contentHandlers.putIfAbsent(mimeType, new ArrayList<ServiceReference>());
                  List<ServiceReference> list = contentHandlers.get(mimeType);
                  synchronized (list)
                  {
                     list.add(reference);
                     Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
                  }
               }
            }
            else
            {
               log.error("A non-compliant instance of " + ContentHandler.class.getName() + " has been registered for mime types: " + Arrays.toString(mimeTypes)
                     + " - " + svc);
            }
            return svc;
         }

         @Override
         public void modifiedService(ServiceReference reference, Object service)
         {
            removedService(reference, service);
            addingService(reference);
         }

         @Override
         public void removedService(ServiceReference reference, Object service)
         {
            super.removedService(reference, service);

            for (List<ServiceReference> list : contentHandlers.values())
            {
               synchronized (list)
               {
                  for (Iterator<ServiceReference> it = list.iterator(); it.hasNext();)
                  {
                     if (it.next().equals(reference))
                     {
                        it.remove();
                        break;
                     }
                  }
               }
            }
         }
      };
      contentServiceTracker.open();
   }

   @Override
   public void stopPlugin()
   {
   }

   @Override
   public URLStreamHandler createURLStreamHandler(String protocol)
   {
      List<ServiceReference> refList = streamHandlers.get(protocol);
      if (refList == null)
         return null;

      return new URLStreamHandlerProxy(protocol, refList);
   }

   @Override
   public ContentHandler createContentHandler(String mimetype)
   {
      List<ServiceReference> refList = contentHandlers.get(mimetype);
      if (refList == null)
         return null;

      synchronized (refList)
      {
         if (refList.isEmpty())
            return null;

         ServiceReference ref = refList.get(0);
         Object service = ref.getBundle().getBundleContext().getService(ref);
         if (service instanceof ContentHandler)
            return (ContentHandler)service;

         return null;
      }
   }

   /**
    * Parses a service registration property with a value which can be of type String or String [].
    * @param prop The property value.
    * @return All the values found in a String [] or null of the property doesn't comply.
    */
   private String[] parseServiceProperty(Object prop)
   {
      if (prop == null)
         return null;

      if (prop instanceof String)
         return new String[] { (String)prop };

      if (prop instanceof String[])
         return (String[])prop;

      return null;
   }

   private static final class URLStreamHandlerProxy extends URLStreamHandler implements URLStreamHandlerSetter
   {
      // This list is maintained in the ServiceTracker that tracks the URLStreamHandlerService
      // This proxy should always use to top element (if it contains any elements).
      private final List<ServiceReference> serviceReferences;
      private final String protocol;

      public URLStreamHandlerProxy(String protocol, List<ServiceReference> refList)
      {
         this.protocol = protocol;
         this.serviceReferences = refList;
      }

      @Override
      public void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path, String query, String ref)
      {
         // Made public to implement URLStreamHandlerSetter
         super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
      }

      @Override
      @SuppressWarnings("deprecation")
      public void setURL(URL u, String protocol, String host, int port, String file, String ref)
      {
         // Made public to implement URLStreamHandlerSetter
         super.setURL(u, protocol, host, port, file, ref);
      }

      @Override
      protected void parseURL(URL u, String spec, int start, int limit)
      {
         getHandlerService().parseURL(this, u, spec, start, limit);
      }

      @Override
      protected URLConnection openConnection(URL u) throws IOException
      {
         return getHandlerService().openConnection(u);
      }

      @Override
      protected String toExternalForm(URL u)
      {
         return getHandlerService().toExternalForm(u);
      }

      @Override
      protected URLConnection openConnection(URL u, Proxy p) throws IOException
      {
         URLStreamHandlerService handler = getHandlerService();
         try
         {
            Method method = handler.getClass().getDeclaredMethod("openConnection", URL.class, Proxy.class);
            return (URLConnection)method.invoke(handler, u, p);
         }
         catch (NoSuchMethodException e)
         {
            throw new IOException("openConnection(URL,Proxy) not found on " + handler, e);
         }
         catch (IllegalAccessException e)
         {
            throw new IOException("openConnection(URL,Proxy) not accessible on " + handler, e);
         }
         catch (InvocationTargetException e)
         {
            throw new IOException("Problem invoking openConnection(URL,Proxy) on " + handler, e);
         }
      }

      @Override
      protected int getDefaultPort()
      {
         return getHandlerService().getDefaultPort();
      }

      @Override
      protected boolean equals(URL u1, URL u2)
      {
         return getHandlerService().equals(u1, u2);
      }

      @Override
      protected int hashCode(URL u)
      {
         return getHandlerService().hashCode(u);
      }

      @Override
      protected boolean sameFile(URL u1, URL u2)
      {
         return getHandlerService().sameFile(u1, u2);
      }

      @Override
      protected synchronized InetAddress getHostAddress(URL u)
      {
         return getHandlerService().getHostAddress(u);
      }

      @Override
      protected boolean hostsEqual(URL u1, URL u2)
      {
         return getHandlerService().hostsEqual(u1, u2);
      }

      private URLStreamHandlerService getHandlerService()
      {
         synchronized (serviceReferences)
         {
            if (serviceReferences.isEmpty())
               throw new IllegalStateException("No handlers in the OSGi Service registry for protocol: " + protocol);

            ServiceReference ref = serviceReferences.get(0);
            Object service = ref.getBundle().getBundleContext().getService(ref);
            if (service instanceof URLStreamHandlerService)
            {
               return (URLStreamHandlerService)service;
            }
            throw new IllegalStateException("Problem with OSGi URL handler service " + service + " for url:" + protocol);
         }
      }
   }
}

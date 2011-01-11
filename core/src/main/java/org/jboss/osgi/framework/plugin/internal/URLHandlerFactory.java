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
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.ServiceReferenceComparator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A {@link URLStreamHandlerFactory} that provides {@link URLStreamHandler} instances
 * which are backed by an OSGi service.
 * The returned handler instances are proxies which allow the URL Stream Handler implementation
 * to be changed at a later point in time (the JRE caches the first URL Stream Handler returned
 * for a given protocol).
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class URLHandlerFactory implements URLStreamHandlerFactory
{
   private static final Logger log = Logger.getLogger(URLHandlerFactory.class);
   private static BundleContext systemBundleContext;
   private static ServiceTracker tracker;
   private static ConcurrentMap<String, List<ServiceReference>> handlers = new ConcurrentHashMap<String, List<ServiceReference>>();

   /**
    * Parses a service registration property with a value which can be of type String
    * or String [].
    * @param prop The property value.
    * @return All the values found in a String [] or null of the property doesn't comply.
    */
   static String[] parseServiceProperty(Object prop)
   {
      if (prop == null)
         return null;

      if (prop instanceof String)
         return new String[] { (String)prop };

      if (prop instanceof String[])
         return (String[])prop;

      return null;
   }

   /**
    * There are two disconnected lifecycles at play here. The creation of an instance of this class through the
    * java.util.ServiceLoader mechanism and the Initialization of this factory from an OSGi point of view.
    * This method activates the class and its associated instance.
    */
   static void initSystemBundleContext(BundleContext bc)
   {
      if (systemBundleContext != null)
      {
         cleanUp();
         // A number of don't properly shut down the system, so I'm doing the cleanup here instead.
         // These tests are mostly found in the umbrella project.
         // TODO fix the shutdown sequence in the tests concerned so that the following error condition
         // can be enabled.
         // throw new IllegalStateException("URL Handler Factory already initialized.");
      }

      systemBundleContext = bc;
      tracker = new ServiceTracker(systemBundleContext, URLStreamHandlerService.class.getName(), null)
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
                  handlers.putIfAbsent(protocol, new ArrayList<ServiceReference>());
                  List<ServiceReference> list = handlers.get(protocol);
                  synchronized (list)
                  {
                     list.add(reference);
                     Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
                  }
               }
            }
            else
            {
               log.error("A non-compliant instance of " + URLStreamHandlerService.class.getName()
                     + " has been registered for protocols: " + Arrays.toString(protocols) + " - " + svc);
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

            for (List<ServiceReference> list : handlers.values())
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
      tracker.open();
   }

   static void cleanUp()
   {
      if (tracker != null)
         tracker.close();

      handlers.clear();
      tracker = null;
      systemBundleContext = null;
   }

   public URLHandlerFactory()
   {
      // Cannot do this check because we don't have full control who exactly registers the
      // framework module with the ModularURLStreamHandlerFactory. However continuing regardless
      // is harmless as there simply won't be any handlers found until we are initialized.
      //
      // if (systemBundleContext == null)
      //   throw new IllegalStateException("URLHandlerFactory used before it was initialized");
   }

   @Override
   public URLStreamHandler createURLStreamHandler(String protocol)
   {
      List<ServiceReference> refList = handlers.get(protocol);
      if (refList == null)
         return null;

      return new URLStreamHandlerProxy(protocol, refList);
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

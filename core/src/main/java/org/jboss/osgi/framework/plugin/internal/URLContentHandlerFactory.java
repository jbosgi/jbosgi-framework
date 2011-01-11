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

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
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
import org.osgi.util.tracker.ServiceTracker;

/**
 * A {@link ContentHandlerFactory} which is backed by OSGi services.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class URLContentHandlerFactory implements ContentHandlerFactory
{
   private final Logger log = Logger.getLogger(URLHandlerFactory.class);
   private ServiceTracker tracker;
   private ConcurrentMap<String, List<ServiceReference>> handlers = new ConcurrentHashMap<String, List<ServiceReference>>();

   public URLContentHandlerFactory(BundleContext systemContext)
   {
      tracker = new ServiceTracker(systemContext, ContentHandler.class.getName(), null)
      {
         @Override
         public Object addingService(ServiceReference reference)
         {
            Object svc = super.addingService(reference);
            String[] mimeTypes = URLHandlerFactory.parseServiceProperty(reference.getProperty(URLConstants.URL_CONTENT_MIMETYPE));
            if (mimeTypes != null && svc instanceof ContentHandler)
            {
               for (String mimeType : mimeTypes)
               {
                  handlers.putIfAbsent(mimeType, new ArrayList<ServiceReference>());
                  List<ServiceReference> list = handlers.get(mimeType);
                  synchronized (list)
                  {
                     list.add(reference);
                     Collections.sort(list, Collections.reverseOrder(ServiceReferenceComparator.getInstance()));
                  }
               }
            }
            else
            {
               log.error("A non-compliant instance of " + ContentHandler.class.getName()
                     + " has been registered for mime types: " + Arrays.toString(mimeTypes) + " - " + svc);
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

   @Override
   protected void finalize() throws Throwable
   {
      tracker.close();
   }

   @Override
   public ContentHandler createContentHandler(String mimetype)
   {
      List<ServiceReference> refList = handlers.get(mimetype);
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
}

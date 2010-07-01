/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.service.support;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * SimpleServiceFactory.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
public class SimpleServiceFactory implements ServiceFactory
{
   Object service;

   public Bundle getBundle;
   public int getCount;

   public Bundle ungetBundle;
   public ServiceRegistration ungetRegistration;
   public Object ungetService;
   public int ungetCount;

   public SimpleServiceFactory(Object service)
   {
      this.service = service;
   }

   public Object getService(Bundle bundle, ServiceRegistration registration)
   {
      getBundle = bundle;
      getCount++;
      return service;
   }

   public void ungetService(Bundle bundle, ServiceRegistration registration, Object unget)
   {
      ungetBundle = bundle;
      ungetRegistration = registration;
      ungetService = unget;
      ungetCount++;
   }

}

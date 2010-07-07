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
package org.jboss.osgi.msc.plugin;

import java.util.Collection;

import org.jboss.osgi.msc.bundle.ServiceState;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

/**
 * A plugin that handles the various OSGi event types.  
 * 
 * @author thomas.diesler@jboss.com
 * @since 27-Aug-2009
 */
public interface FrameworkEventsPlugin extends Plugin
{
   boolean isActive();
   
   void setActive(boolean active);
   
   void addBundleListener(Bundle bundle, BundleListener listener);

   void removeBundleListener(Bundle bundle, BundleListener listener);
   
   void removeBundleListeners(Bundle bundle);

   void addFrameworkListener(Bundle bundle, FrameworkListener listener);

   void removeFrameworkListener(Bundle bundle, FrameworkListener listener);

   void removeFrameworkListeners(Bundle bundle);

   void addServiceListener(Bundle bundle, ServiceListener listener, String filter) throws InvalidSyntaxException;

   Collection<ListenerInfo> getServiceListenerInfos(Bundle bundle);
   
   void removeServiceListener(Bundle bundle, ServiceListener listener);
   
   void removeServiceListeners(Bundle bundle);
   
   void fireBundleEvent(Bundle bundle, int type);

   void fireFrameworkEvent(Bundle bundle, int type, Throwable throwable);

   void fireServiceEvent(Bundle bundle, int type, ServiceState service);
}
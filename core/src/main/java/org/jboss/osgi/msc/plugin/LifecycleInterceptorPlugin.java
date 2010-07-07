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

import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.osgi.framework.Bundle;

/**
 * The LifecycleInterceptorService service plugin
 * 
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public interface LifecycleInterceptorPlugin extends ServicePlugin, LifecycleInterceptorService
{
   /**
    * Invoke the registered set of interceptors for the given bundle state change.
    *  
    * @param state The future state of the bundle
    * @param bundle The bundle that changes state
    * @throws LifecycleInterceptorException if the invocation of an interceptor fails 
    */
   void handleStateChange(int state, Bundle bundle);
}
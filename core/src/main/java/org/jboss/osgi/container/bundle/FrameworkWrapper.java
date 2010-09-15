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
package org.jboss.osgi.container.bundle;

//$Id$

import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * A generic Framework wrapper that delegates all method calls to the underlying
 * Framework implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Oct-2009
 */
public class FrameworkWrapper extends BundleWrapper implements Framework
{
   public FrameworkWrapper(FrameworkState framework)
   {
      super(framework);
   }

   @Override
   public void init() throws BundleException
   {
      Framework framework = (Framework)getBundleState();
      framework.init();
   }

   @Override
   public FrameworkEvent waitForStop(long timeout) throws InterruptedException
   {
      Framework framework = (Framework)getBundleState();
      return framework.waitForStop(timeout);
   }
}
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
package org.jboss.osgi.felix.resolver;

import java.util.List;
import java.util.Map;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.resolver.Module;
import org.apache.felix.framework.resolver.Resolver;
import org.apache.felix.framework.resolver.ResolverImpl;
import org.apache.felix.framework.resolver.Wire;

/**
 * An extension to the Apache Felix Resolver.
 *  
 * @author thomas.diesler@jboss.com
 * @since 31-May-2010
 */
class AbstractResolver implements Resolver
{
   private Resolver delegate;

   public AbstractResolver(Logger logger)
   {
      this.delegate = new ResolverImpl(logger);
   }

   @Override
   public Map<Module, List<Wire>> resolve(ResolverState state, Module module)
   {
      return delegate.resolve(state, module);
   }

   @Override
   public Map<Module, List<Wire>> resolve(ResolverState state, Module module, String pkgName)
   {
      return delegate.resolve(state, module, pkgName);
   }
}
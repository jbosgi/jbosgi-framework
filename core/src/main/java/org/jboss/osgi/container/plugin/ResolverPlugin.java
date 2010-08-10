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
package org.jboss.osgi.container.plugin;

// $Id$

import java.util.List;

import org.jboss.osgi.container.bundle.Revision;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.BundleException;

/**
 * The resolver plugin.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2009
 */
public interface ResolverPlugin extends Plugin 
{
   /**
    * Get the resolver instance
    */
   XResolver getResolver();

   /**
    * Add a bundle revision to the resolver.
    * @param revision the bundle revision
    */
   void addRevision(Revision revision);

   /**
    * Remove a bundle revision from the resolver.
    * @param revision the bundle revision
    */
   void removeRevision(Revision revision);
   
   /**
    * Resolve the given bundle revision.
    * @param revision the bundle revision to resolve
    * @throws BundleException If the bundle could not get resolved
    */
   void resolve(Revision revision) throws BundleException;

   /**
    * Resolve the given list of bundle revisions.
    * @param revisions the bundle revisions to resolve
    * @return The list of resolved revisions in the resolve order or an empty list
    */
   List<Revision> resolve(List<Revision> revisions);
}
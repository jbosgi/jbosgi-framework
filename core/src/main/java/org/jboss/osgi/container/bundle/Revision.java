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
package org.jboss.osgi.container.bundle;

import org.jboss.osgi.resolver.XModule;

/**
 * A {@link Revision} is the interface between a bundle and the resolver plugin. 
 * A bundle can have multiple revisions that which concurrently need to be
 * able to participate in the resolution process. So per bundle, multiple
 * revision objects can be registered with the resolver plugin.<p/>
 * 
 * Each {@link Revision} has an associated {@link XModule} instance.
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public interface Revision
{
   /**
    * A framework-wide unique ID for this revision.
    * The System Bundle has only 1 revision which has the number 0
    * @return the unique revision ID.
    */
   int getGlobalRevisionId();

   /**
    * The revision number of a bundle or fragment. Since bundles can have multiple revisions
    * this number indicates what revision of a particular bundle this object represents.
    * Every bundle starts with revision number 0. 
    * @return the revision of this bundle or fragment.
    */
   int getUpdateCount();

   /**
    * Return the associated {@link XModule} instance.
    * @return the {@link XModule} instance.
    */
   XModule getResolverModule();
}

package org.jboss.osgi.container.bundle;

import org.jboss.osgi.resolver.XModule;

public interface Revision
{
   int getRevisionID();

   int getRevision();

   XModule getResolverModule();
}

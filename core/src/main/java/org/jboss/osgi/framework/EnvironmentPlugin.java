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
package org.jboss.osgi.framework;

import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Resource;

import java.util.Collection;

/**
 * The environment plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
public interface EnvironmentPlugin extends XEnvironment {

    /**
     * Currently, the client must have knowlege about the fragments that exist in the environment
     * and pass them in as optional resources. We extend the environment to find the fragments
     * that can potentially attach to the given host capabilities.
     */
    Collection<? extends Resource> findAttachableFragments(Collection<? extends Capability> hosts);

    Collection<Resource> filterSingletons(Collection<? extends Resource> resources);
}
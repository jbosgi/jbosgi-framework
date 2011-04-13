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
package org.jboss.osgi.framework.internal;

import org.jboss.osgi.spi.framework.GenericBundleWrapper;
import org.osgi.framework.BundleContext;

/**
 * A bundle wrapper that delegates all method calls to the underlying Bundle implementation.
 * 
 * This is a workaround for
 * 
 *   Invalid discovery of Bundle.getBundleContext() method
 *   http://issues.ops4j.org/browse/PAXSB-44
 *   
 * [TODO] Remove BundleWrapper
 * 
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class BundleWrapper<T extends AbstractBundleState> extends GenericBundleWrapper<T> {

    BundleWrapper(T bundleState) {
        super(bundleState);
    }

    AbstractBundleState getBundleState() {
        return getWrappedBundle();
    }

    public BundleContext getBundleContext() {
        return super.getBundleContext();
    }
}
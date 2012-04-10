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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * This is the internal implementation of a fragment Bundle.
 *
 * Fragment specific functionality is handled here.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleState extends UserBundleState {

    FragmentBundleState(FrameworkState frameworkState, long bundleId, Deployment dep) {
        super(frameworkState, bundleId, dep);
    }

    static FragmentBundleState assertBundleState(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        assert bundleState instanceof FragmentBundleState : "Not a FragmentBundleState: " + bundleState;
        return (FragmentBundleState) bundleState;
    }

    @Override
    AbstractBundleContext createContextInternal() {
        return new FragmentBundleContext(this);
    }

    @Override
    void initUserBundleState(OSGiMetaData metadata) {
        // do nothing
    }

    @Override
    FragmentBundleRevision createRevisionInternal(Deployment deployment) throws BundleException {
        return new FragmentBundleRevision(this, deployment);
    }

    @Override
    FragmentBundleRevision getCurrentBundleRevision() {
        return (FragmentBundleRevision) super.getCurrentBundleRevision();
    }

    @Override
    boolean isFragment() {
        return true;
    }

    @Override
    void startInternal(int options) throws BundleException {
        throw MESSAGES.bundleCannotStartFragment();
    }

    @Override
    void stopInternal(int options) throws BundleException {
        throw MESSAGES.bundleCannotStopFragment();
    }
}

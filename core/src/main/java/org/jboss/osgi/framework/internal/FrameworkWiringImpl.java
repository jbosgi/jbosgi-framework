/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.LockUtils;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XWiringSupport;
import org.jboss.osgi.resolver.spi.AbstractBundleWire;
import org.jboss.osgi.resolver.spi.ResolverHookException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;

/**
 * An implementation of the {@link FrameworkWiringSupport} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 08-Nov-2012
 */
public final class FrameworkWiringImpl implements FrameworkWiring {

    private final BundleManagerPlugin bundleManager;
    private final FrameworkEvents events;
    private final XResolver resolver;
    private final LockManager lockManager;
    private final XEnvironment environment;
    private final ExecutorService executorService;

    public FrameworkWiringImpl(BundleManager bundleManager, FrameworkEvents events, XEnvironment environment, XResolver resolver, LockManager lockManager, ExecutorService executorService) {
        this.bundleManager = (BundleManagerPlugin) bundleManager;
        this.events = events;
        this.environment = environment;
        this.resolver = resolver;
        this.lockManager = lockManager;
        this.executorService = executorService;
    }

    @Override
    public Bundle getBundle() {
        return bundleManager.getSystemBundle();
    }

    @Override
    public void refreshBundles(final Collection<Bundle> bundles, final FrameworkListener... listeners) {

        final List<Bundle> bundlesToRefresh = new ArrayList<Bundle>();
        final List<XBundle> dependencyClosure = new ArrayList<XBundle>();

        LockContext lockContext = lockEnvironment(LockManager.Method.REFRESH);
        try {
            if (bundles == null) {
                bundlesToRefresh.addAll(getRemovalPendingBundles());
            } else {
                bundlesToRefresh.addAll(bundles);
            }

            // Compute all depending bundles that need to be stopped and unresolved.
            for (Bundle auxBundle : getDependencyClosure(bundlesToRefresh)) {
                XBundle bundle = (XBundle) auxBundle;
                dependencyClosure.add(bundle);
            }
        } finally {
            unlockEnvironment(lockContext);
        }

        LOGGER.debugf("Refresh bundles %s", bundlesToRefresh);
        LOGGER.debugf("Dependency closure %s", dependencyClosure);

        Runnable runner = new Runnable() {
            public void run() {
                refreshBundlesInternal(dependencyClosure, listeners);
            }
        };

        if (!executorService.isShutdown()) {
            executorService.execute(runner);
            //runner.run();
        }
    }

    private void refreshBundlesInternal(List<XBundle> dependencyClosure, FrameworkListener... listeners) {

        List<XBundle> stopList = new ArrayList<XBundle>();
        List<XBundle> uninstallBundles = new ArrayList<XBundle>();

        for (Bundle auxBundle : dependencyClosure) {
            XBundle bundle = (XBundle) auxBundle;
            int state = bundle.getState();
            if (state == Bundle.UNINSTALLED) {
                uninstallBundles.add(bundle);
            } else if (state == Bundle.ACTIVE || state == Bundle.STARTING) {
                stopList.add(bundle);
            }
        }

        LockableItem wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
        XBundle[] bundles = dependencyClosure.toArray(new XBundle[dependencyClosure.size()]);
        LockableItem[] items = LockUtils.getLockableItems(bundles, new LockableItem[] { wireLock });
        LockContext context = lockManager.lockItems(Method.REFRESH, items);
        try {
            // Lock the dependency closure
            BundleStartLevelComparator startLevelComparator = new BundleStartLevelComparator();
            Collections.sort(stopList, startLevelComparator);

            for (ListIterator<XBundle> it = stopList.listIterator(stopList.size()); it.hasPrevious();) {
                XBundle bundle = it.previous();
                try {
                    bundleManager.stopBundleLifecycle(bundle, Bundle.STOP_TRANSIENT);
                } catch (Exception th) {
                    events.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, th);
                }
            }

            for (XBundle bundle : uninstallBundles) {
                if (bundle instanceof UserBundleState) {
                    bundleManager.uninstallBundle(bundle, 0);
                } else {
                    XBundleRevision current = bundle.getBundleRevision();
                    BundleRevisions brevs = bundle.adapt(BundleRevisions.class);
                    for (BundleRevision aux : brevs.getRevisions()) {
                        XBundleRevision brev = (XBundleRevision) aux;
                        if (brev != current) {
                            bundleManager.removeRevisionLifecycle(brev, 0);
                        }
                    }
                }
                dependencyClosure.remove(bundle);
            }

            for (XBundle bundle : dependencyClosure) {
                if (bundle instanceof UserBundleState) {
                    try {
                        UserBundleState userBundle = (UserBundleState) bundle;
                        userBundle.refresh();
                    } catch (Exception th) {
                        events.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, th);
                    }
                } else {
                    // nothing to do for adapted modules
                }
            }

            for (XBundle bundle : stopList) {
                try {
                    bundleManager.startBundleLifecycle(bundle, Bundle.START_TRANSIENT);
                } catch (Exception th) {
                    events.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, th);
                }
            }

            XBundle systemBundle = bundleManager.getSystemBundle();
            events.fireFrameworkEvent(systemBundle, FrameworkEvent.PACKAGES_REFRESHED, null, listeners);
        } catch (BundleException ex) {
            throw MESSAGES.illegalStateCannotRefreshBundles(ex);
        } finally {
            lockManager.unlockItems(context);
        }
    }

    @Override
    public boolean resolveBundles(Collection<Bundle> bundles) {

        // Only bundles that are in state INSTALLED qualify as resolvable
        if (bundles == null) {
            Set<XBundle> bundleset = bundleManager.getBundles(Bundle.INSTALLED);
            bundles = new ArrayList<Bundle>(bundleset);
        }

        LOGGER.debugf("Resolve bundles %s", bundles);

        // Get the set of resolvable bundle revisions
        Set<BundleRevision> resolvableRevisions = new LinkedHashSet<BundleRevision>();
        for (Bundle aux : bundles) {
            XBundle bundleState = (XBundle) aux;
            resolvableRevisions.add(bundleState.getBundleRevision());
        }

        boolean result = true;
        try {
            XResolveContext context = resolver.createResolveContext(environment, null, resolvableRevisions);
            resolver.resolveAndApply(context);
            for (BundleRevision aux : resolvableRevisions) {
                if (aux.getWiring() == null) {
                    LOGGER.debugf("Cannot resolve: %s", aux);
                    result = false;
                }
            }
        } catch (ResolutionException ex) {
            LOGGER.debugf(ex, "Cannot resolve: %s", resolvableRevisions);
            result = false;
        } catch (ResolverHookException ex) {
            LOGGER.debugf(ex, "Cannot resolve: %s", resolvableRevisions);
            result = false;
        }
        return result;
    }

    /*
     * Returns the bundles that have non-current, in use bundle wirings. This is typically the bundles which have been updated
     * or uninstalled since the last call to refreshBundles
     */
    @Override
    public Collection<Bundle> getRemovalPendingBundles() {
        Collection<Bundle> result = new HashSet<Bundle>();
        for (XBundle bundle : bundleManager.getBundles(null)) {
            if (bundle.getBundleId() != 0) {
                BundleRevisions revisions = bundle.adapt(BundleRevisions.class);
                for (BundleRevision rev : revisions.getRevisions()) {
                    XBundleRevision brev = (XBundleRevision) rev;
                    XWiringSupport wiringSupport = brev.getWiringSupport();
                    if (!wiringSupport.isEffective()) {
                        BundleWiring bwiring = (BundleWiring) wiringSupport.getWiring(false);
                        if (bwiring != null && bwiring.isInUse()) {
                            result.add(bundle);
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Collection<Bundle> getDependencyClosure(Collection<Bundle> bundles) {
        if (bundles == null)
            throw MESSAGES.illegalArgumentNull("bundles");

        Set<Bundle> closure = new HashSet<Bundle>();
        for (Bundle bundle : bundles) {
            transitiveDependencyClosure((XBundle) bundle, closure);
        }
        return Collections.unmodifiableCollection(closure);
    }

    private void transitiveDependencyClosure(XBundle bundle, Set<Bundle> closure) {
        if (bundle.getBundleId() != 0 && closure.contains(bundle) == false) {
            closure.add(bundle);

            BundleRevisions brevs = bundle.adapt(BundleRevisions.class);
            for (BundleRevision aux : brevs.getRevisions()) {
                XBundleRevision brev = (XBundleRevision) aux;
                Wiring wiring = brev.getWiringSupport().getWiring(false);
                if (wiring != null) {
                    transitiveDependencyClosure(brev, wiring, closure);
                }
            }
        }
    }

    private void transitiveDependencyClosure(XBundleRevision brev, Wiring wiring, Set<Bundle> closure) {
        if (brev instanceof FragmentBundleRevision) {
            for (Wire wire : wiring.getRequiredResourceWires(HostNamespace.HOST_NAMESPACE)) {
                AbstractBundleWire bwire = (AbstractBundleWire) wire;
                Bundle provider = bwire.getProviderWiring(false).getBundle();
                transitiveDependencyClosure((XBundle) provider, closure);
            }
        } else {
            for (Wire wire : wiring.getProvidedResourceWires(null)) {
                XBundleRevision requirer = (XBundleRevision) wire.getRequirer();
                transitiveDependencyClosure(requirer.getBundle(), closure);
            }
        }
    }

    private LockContext lockEnvironment(Method method, XResource... resources) {
        FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
        return lockManager.lockItems(method, getLockableItems(wireLock, resources));
    }

    private LockableItem[] getLockableItems(LockableItem item, XResource... resources) {
        List<LockableItem> items = new ArrayList<LockableItem>();
        items.add(item);
        if (resources != null) {
            for (XResource res : resources) {
                XBundleRevision brev = (XBundleRevision) res;
                if (brev.getBundle() instanceof LockableItem) {
                    items.add((LockableItem) brev.getBundle());
                }
            }
        }
        return items.toArray(new LockableItem[items.size()]);
    }

    private void unlockEnvironment(LockContext context) {
        lockManager.unlockItems(context);
    }

    private static class BundleStartLevelComparator implements Comparator<Bundle> {
        @Override
        public int compare(Bundle o1, Bundle o2) {
            int sl1 = o1.adapt(BundleStartLevel.class).getStartLevel();
            int sl2 = o2.adapt(BundleStartLevel.class).getStartLevel();
            return sl1 - sl2 != 0 ? sl1 - sl2 : (int) (o1.getBundleId() - o2.getBundleId());
        }
    }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XPackageCapability;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.service.resolver.ResolutionException;

/**
 * An implementation of the {@link PackageAdmin} service.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2010
 */
public final class PackageAdminPlugin extends AbstractExecutorService<PackageAdmin> implements PackageAdmin {

    // Provide logging
    static final Logger log = Logger.getLogger(PackageAdminPlugin.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<ResolverPlugin> injectedResolver = new InjectedValue<ResolverPlugin>();
    private ServiceRegistration registration;

    static void addService(ServiceTarget serviceTarget) {
        PackageAdminPlugin service = new PackageAdminPlugin();
        ServiceBuilder<PackageAdmin> builder = serviceTarget.addService(Services.PACKAGE_ADMIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, service.injectedFrameworkEvents);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(Services.SYSTEM_CONTEXT, BundleContext.class, service.injectedSystemContext);
        builder.addDependency(InternalServices.RESOLVER_PLUGIN, ResolverPlugin.class, service.injectedResolver);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private PackageAdminPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(PackageAdmin.class.getName(), this, null);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        registration.unregister();
    }

    @Override
    public PackageAdminPlugin getValue() {
        return this;
    }

    @Override
    ExecutorService createExecutorService() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable run) {
                Thread thread = new Thread(run);
                thread.setName("OSGi PackageAdmin refresh Thread");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public ExportedPackage[] getExportedPackages(Bundle bundle) {
        if (bundle == null)
            return getAllExportedPackages();

        // A bundle is destroyed if it is no longer known to the system.
        // An uninstalled bundle can potentially live on if there are
        // other bundles depending on it. Only after a call to
        // {@link PackageAdmin#refreshPackages(Bundle[])} the bundle gets destroyed.
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        BundleManager bundleManager = injectedBundleManager.getValue();
        if (bundleManager.getBundleById(bundle.getBundleId()) == null)
            return null;

        List<ExportedPackage> result = new ArrayList<ExportedPackage>();
        for (AbstractBundleRevision brev : bundleState.getAllBundleRevisions()) {
            if (brev.isResolved()) {
                for (Capability cap : brev.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE))
                    result.add(new ExportedPackageImpl((XPackageCapability) cap));
            }
        }

        // mandated by spec
        if (result.size() == 0)
            return null;

        return result.toArray(new ExportedPackage[result.size()]);
    }

    private ExportedPackage[] getAllExportedPackages() {
        List<ExportedPackage> result = new ArrayList<ExportedPackage>();
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (AbstractBundleState ab : bundleManager.getBundles()) {
            ExportedPackage[] pkgs = getExportedPackages(ab);
            if (pkgs != null)
                result.addAll(Arrays.asList(pkgs));
        }

        return result.toArray(new ExportedPackage[result.size()]);
    }

    @Override
    public ExportedPackage[] getExportedPackages(String name) {
        ExportedPackage[] pkgs = getExportedPackagesInternal(name);
        return pkgs.length == 0 ? null : pkgs;
    }

    private ExportedPackage[] getExportedPackagesInternal(String name) {
        if (name == null)
            throw new IllegalArgumentException("Null name");

        Set<ExportedPackage> result = new HashSet<ExportedPackage>();
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (AbstractBundleState bundleState : bundleManager.getBundles(null)) {
            for (AbstractBundleRevision brev : bundleState.getAllBundleRevisions()) {
                if (brev.isResolved() && !brev.isFragment()) {
                    for (Capability cap : brev.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                        XPackageCapability xcap = (XPackageCapability) cap;
                        if (xcap.getPackageName().equals(name)) {
                            result.add(new ExportedPackageImpl(xcap));
                        }
                    }
                }
            }
        }
        return result.toArray(new ExportedPackage[result.size()]);
    }

    @Override
    public ExportedPackage getExportedPackage(String name) {
        // This implementation is flawed but the design of this API in PackageAdmin
        // is also flawed and from 4.3 deprecated so we're doing a best effort
        ExportedPackage[] exported = getExportedPackagesInternal(name);
        List<ExportedPackage> wired = new ArrayList<ExportedPackage>();
        List<ExportedPackage> notWired = new ArrayList<ExportedPackage>();

        for (ExportedPackage ep : exported) {
            XPackageCapability cap = ((ExportedPackageImpl) ep).getCapability();
            if (isWired(cap))
                wired.add(ep);
            else
                notWired.add(ep);
        }
        ExportedPackageComparator comparator = new ExportedPackageComparator();
        Collections.sort(wired, comparator);
        Collections.sort(notWired, comparator);

        if (wired.size() > 0)
            return wired.get(0);
        else if (notWired.size() > 0)
            return notWired.get(0);
        else
            return null;
    }

    private boolean isWired(XPackageCapability cap) {
        BundleWiring wiring = ((BundleRevision) cap.getResource()).getWiring();
        if (wiring != null) {
            for (Wire wire : wiring.getProvidedResourceWires(cap.getNamespace())) {
                if (wire.getCapability() == cap)
                    return true;
            }
            List<Wire> requireBundleWires = wiring.getProvidedResourceWires(BundleNamespace.BUNDLE_NAMESPACE);
            if (requireBundleWires.size() > 0)
                return true;
        }
        return false;
    }

    @Override
    public void refreshPackages(final Bundle[] bundlesToRefresh) {
        final BundleManager bundleManager = injectedBundleManager.getValue();
        final FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
        Runnable runner = new Runnable() {

            @Override
            public void run() {
                Bundle[] bundles = bundlesToRefresh;
                if (bundles == null) {
                    // 4.2 core spec 7.5.3.11 on null:
                    // all bundles updated or uninstalled since the last call to this method.

                    List<UserBundleState> bundlesToRefresh = new ArrayList<UserBundleState>();
                    for (AbstractBundleState aux : bundleManager.getBundles(null)) {
                        if (aux.getBundleId() != 0) {
                            UserBundleState userBundle = (UserBundleState) aux;
                            // a bundle with more than 1 revision has been updated since the last refresh packages call
                            if (userBundle.getAllBundleRevisions().size() > 1 || aux.getState() == Bundle.UNINSTALLED)
                                bundlesToRefresh.add(userBundle);
                        }
                    }
                    bundles = bundlesToRefresh.toArray(new Bundle[bundlesToRefresh.size()]);
                }

                Set<UserBundleState> providedBundles = new LinkedHashSet<UserBundleState>();
                for (Bundle aux : bundles) {
                    UserBundleState bundleState = UserBundleState.assertBundleState(aux);
                    providedBundles.add(bundleState);
                }

                Set<HostBundleState> stopBundles = new HashSet<HostBundleState>();
                Set<UserBundleState> refreshBundles = new HashSet<UserBundleState>();
                Set<UserBundleState> uninstallBundles = new HashSet<UserBundleState>();

                for (UserBundleState userBundle : providedBundles) {
                    if (userBundle.getState() == Bundle.UNINSTALLED)
                        uninstallBundles.add(userBundle);
                    else if (userBundle.isResolved() == true)
                        refreshBundles.add(userBundle);
                }

                // Compute all depending bundles that need to be stopped and unresolved.
                for (AbstractBundleState aux : bundleManager.getBundles()) {
                    if (aux instanceof HostBundleState && aux.isResolved()) {
                        HostBundleState hostBundle = HostBundleState.assertBundleState(aux);
                        for (UserBundleState depBundle : hostBundle.getDependentBundles()) {
                            if (providedBundles.contains(depBundle)) {
                                // Bundles can be either ACTIVE or RESOLVED
                                int state = hostBundle.getState();
                                if (state == Bundle.ACTIVE || state == Bundle.STARTING) {
                                    stopBundles.add(hostBundle);
                                }
                                refreshBundles.add(hostBundle);
                                break;
                            }
                        }
                    }
                }

                // Add relevant bundles to be refreshed also to the stop list.
                for (UserBundleState aux : refreshBundles) {
                    if (aux instanceof HostBundleState) {
                        int state = aux.getState();
                        if (state == Bundle.ACTIVE || state == Bundle.STARTING) {
                            stopBundles.add((HostBundleState) aux);
                        }
                    }
                }

                List<HostBundleState> stopList = new ArrayList<HostBundleState>(stopBundles);
                List<UserBundleState> refreshList = new ArrayList<UserBundleState>(refreshBundles);

                BundleStartLevelComparator startLevelComparator = new BundleStartLevelComparator();
                Collections.sort(stopList, startLevelComparator);

                for (ListIterator<HostBundleState> it = stopList.listIterator(stopList.size()); it.hasPrevious();) {
                    HostBundleState hostBundle = it.previous();
                    try {
                        hostBundle.stop(Bundle.STOP_TRANSIENT);
                    } catch (Exception th) {
                        eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, th);
                    }
                }

                for (UserBundleState userBundle : uninstallBundles) {
                    bundleManager.removeBundle(userBundle, 0);
                }

                for (UserBundleState userBundle : refreshList) {
                    try {
                        userBundle.refresh();
                    } catch (Exception th) {
                        eventsPlugin.fireFrameworkEvent(userBundle, FrameworkEvent.ERROR, th);
                    }
                }

                for (HostBundleState hostBundle : stopList) {
                    try {
                        hostBundle.start(Bundle.START_TRANSIENT);
                    } catch (Exception th) {
                        eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, th);
                    }
                }

                eventsPlugin.fireFrameworkEvent(bundleManager.getSystemBundle(), FrameworkEvent.PACKAGES_REFRESHED, null);
            }
        };
        runner.run();
        // getExecutorService().execute(runner);
    }

    @Override
    public boolean resolveBundles(Bundle[] bundles) {
        // Only bundles that are in state INSTALLED and are
        // registered with the resolver qualify as resolvable
        ResolverPlugin resolverPlugin = injectedResolver.getValue();
        BundleManager bundleManager = injectedBundleManager.getValue();
        if (bundles == null) {
            Set<AbstractBundleState> bset = bundleManager.getBundles(Bundle.INSTALLED);
            bundles = new Bundle[bset.size()];
            bset.toArray(bundles);
        }
        Set<Resource> resolve = new LinkedHashSet<Resource>();
        for (Bundle aux : bundles) {
            AbstractBundleState bundleState = AbstractBundleState.assertBundleState(aux);
            resolve.add(bundleState.getCurrentBundleRevision());
        }

        boolean result = true;
        log.debugf("Resolve bundles: %s", resolve);
        try {
            resolverPlugin.resolveAndApply(resolve, null);
            for (Resource aux : resolve) {
                if (((AbstractBundleRevision) aux).isResolved() == false) {
                    result = false;
                    break;
                }
            }
        } catch (ResolutionException ex) {
            log.debugf(ex, "Cannot resolve: " + resolve);
            result = false;
        }
        return result;
    }

    @Override
    public RequiredBundle[] getRequiredBundles(String symbolicName) {
        BundleManager bundleManager = injectedBundleManager.getValue();
        List<HostBundleState> matchingHosts = new ArrayList<HostBundleState>();
        if (symbolicName != null) {
            for (AbstractBundleState aux : bundleManager.getBundles(symbolicName, null)) {
                if (aux instanceof HostBundleState)
                    matchingHosts.add((HostBundleState) aux);
            }
        } else {
            for (AbstractBundleState aux : bundleManager.getBundles()) {
                if (aux instanceof HostBundleState)
                    matchingHosts.add((HostBundleState) aux);
            }
        }
        if (matchingHosts.isEmpty()) {
            return null;
        }
        List<RequiredBundle> result = new ArrayList<RequiredBundle>();
        Iterator<HostBundleState> hostit = matchingHosts.iterator();
        while (hostit.hasNext()) {
            AbstractBundleState bundleState = hostit.next();
            BundleRevision brev = bundleState.getCurrentBundleRevision();
            Set<AbstractBundleState> requiringBundles = new HashSet<AbstractBundleState>();
            BundleWiring wiring = brev.getWiring();
            if (wiring != null) {
                List<Wire> providedWires = wiring.getProvidedResourceWires(BundleNamespace.BUNDLE_NAMESPACE);
                for (Wire wire : providedWires) {
                    Bundle bundle = ((BundleRevision) wire.getRequirer()).getBundle();
                    requiringBundles.add(AbstractBundleState.assertBundleState(bundle));
                }
            }
            result.add(new RequiredBundleImpl(bundleState, requiringBundles));
        }
        return result.toArray(new RequiredBundle[matchingHosts.size()]);
    }

    /**
     * Returns the bundles with the specified symbolic name whose bundle version is within the specified version range. If no
     * bundles are installed that have the specified symbolic name, then <code>null</code> is returned. If a version range is
     * specified, then only the bundles that have the specified symbolic name and whose bundle versions belong to the specified
     * version range are returned. The returned bundles are ordered by version in descending version order so that the first
     * element of the array contains the bundle with the highest version.
     * 
     * @param symbolicName The symbolic name of the desired bundles.
     * @param versionRange The version range of the desired bundles, or <code>null</code> if all versions are desired.
     * @return An array of bundles with the specified name belonging to the specified version range ordered in descending
     *         version order, or <code>null</code> if no bundles are found.
     */
    @Override
    public Bundle[] getBundles(String symbolicName, String versionRange) {
        Set<Bundle> sortedSet = new TreeSet<Bundle>(new Comparator<Bundle>() {
            // Makes sure that the bundles are sorted correctly in the returned array
            // Matching bundles with the highest version should come first.
            public int compare(Bundle b1, Bundle b2) {
                return b2.getVersion().compareTo(b1.getVersion());
            }
        });
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (AbstractBundleState bundleState : bundleManager.getBundles(symbolicName, versionRange)) {
            if (bundleState.getState() != Bundle.UNINSTALLED) {
                sortedSet.add(bundleState);
            }
        }
        if (sortedSet.isEmpty())
            return null;

        return sortedSet.toArray(new Bundle[sortedSet.size()]);
    }

    @Override
    public Bundle[] getFragments(Bundle bundle) {
        // If the specified bundle is a fragment then null is returned.
        // If the specified bundle is not resolved then null is returned
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        if (bundle.getBundleId() == 0 || bundleState.isFragment() || !bundleState.isResolved())
            return null;

        HostBundleState hostBundle = HostBundleState.assertBundleState(bundleState);
        HostBundleRevision curRevision = hostBundle.getCurrentBundleRevision();

        List<Bundle> result = new ArrayList<Bundle>();
        for (FragmentBundleRevision aux : curRevision.getAttachedFragments())
            result.add(aux.getBundleState());

        if (result.isEmpty())
            return null;

        return result.toArray(new Bundle[result.size()]);
    }

    @Override
    public Bundle[] getHosts(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        if (bundleState.isFragment() == false)
            return null;

        FragmentBundleState fragBundle = FragmentBundleState.assertBundleState(bundleState);
        FragmentBundleRevision curRevision = fragBundle.getCurrentBundleRevision();

        List<Bundle> result = new ArrayList<Bundle>();
        for (HostBundleRevision aux : curRevision.getAttachedHosts())
            result.add(aux.getBundleState());

        if (result.isEmpty())
            return null;

        return result.toArray(new Bundle[result.size()]);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Bundle getBundle(Class clazz) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        AbstractBundleState bundleState = moduleManager.getBundleState(clazz);
        return bundleState != null ? bundleState : null;
    }

    @Override
    public int getBundleType(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        return bundleState.isFragment() ? BUNDLE_TYPE_FRAGMENT : 0;
    }

    static class ExportedPackageImpl implements ExportedPackage {

        private final XPackageCapability capability;

        ExportedPackageImpl(XPackageCapability cap) {
            capability = cap;
        }

        @Override
        public String getName() {
            return capability.getPackageName();
        }

        @Override
        public Bundle getExportingBundle() {
            return ((BundleRevision) capability.getResource()).getBundle();
        }

        @Override
        public Bundle[] getImportingBundles() {
            if (isRemovalPending())
                return null;

            Set<Bundle> bundles = new HashSet<Bundle>();
            BundleWiring wiring = ((BundleRevision) capability.getResource()).getWiring();
            for (Wire wire : wiring.getProvidedResourceWires(capability.getNamespace())) {
                AbstractBundleRevision req = (AbstractBundleRevision) wire.getRequirer();
                bundles.add(req.getBundle());
            }
            for (Wire wire : wiring.getProvidedResourceWires(BundleNamespace.BUNDLE_NAMESPACE)) {
                AbstractBundleRevision req = (AbstractBundleRevision) wire.getRequirer();
                bundles.add(req.getBundle());
            }

            return bundles.toArray(new Bundle[bundles.size()]);
        }

        @Override
        public String getSpecificationVersion() {
            return capability.getVersion().toString();
        }

        @Override
        public Version getVersion() {
            return capability.getVersion();
        }

        @Override
        public boolean isRemovalPending() {
            AbstractBundleRevision brev = (AbstractBundleRevision) capability.getResource();
            AbstractBundleState bundleState = brev.getBundleState();
            return brev != bundleState.getCurrentBundleRevision() || bundleState.getState() == Bundle.UNINSTALLED;
        }

        private XPackageCapability getCapability() {
            return capability;
        }

        @Override
        public String toString() {
            return "ExportedPackage[" + capability + "]";
        }
    }

    static class RequiredBundleImpl implements RequiredBundle {

        private final Bundle requiredBundle;
        private final Bundle[] requiringBundles;
        private final AbstractBundleRevision bundleRevision;

        RequiredBundleImpl(AbstractBundleState requiredBundle, Collection<AbstractBundleState> requiringBundles) {
            this.requiredBundle = AbstractBundleState.assertBundleState(requiredBundle);
            this.bundleRevision = requiredBundle.getCurrentBundleRevision();

            List<Bundle> bundles = new ArrayList<Bundle>(requiringBundles.size());
            for (AbstractBundleState ab : requiringBundles) {
                bundles.add(AbstractBundleState.assertBundleState(ab));
            }
            this.requiringBundles = bundles.toArray(new Bundle[bundles.size()]);
        }

        @Override
        public String getSymbolicName() {
            return requiredBundle.getSymbolicName();
        }

        @Override
        public Bundle getBundle() {
            if (isRemovalPending())
                return null;

            return requiredBundle;
        }

        @Override
        public Bundle[] getRequiringBundles() {
            if (isRemovalPending())
                return null;

            return requiringBundles;
        }

        @Override
        public Version getVersion() {
            return requiredBundle.getVersion();
        }

        @Override
        public boolean isRemovalPending() {
            if (requiredBundle.getState() == Bundle.UNINSTALLED)
                return true;

            return !bundleRevision.equals(bundleRevision.getBundleState().getCurrentBundleRevision());
        }

        @Override
        public String toString() {
            return "RequiredBundle[" + requiredBundle + "]";
        }
    }

    private static class BundleStartLevelComparator implements Comparator<HostBundleState> {

        @Override
        public int compare(HostBundleState o1, HostBundleState o2) {
            int sl1 = o1.getStartLevel();
            int sl2 = o2.getStartLevel();
            return sl1 < sl2 ? -1 : (sl1 == sl2 ? 0 : 1);
        }
    }

    private static class ExportedPackageComparator implements Comparator<ExportedPackage> {

        @Override
        public int compare(ExportedPackage ep1, ExportedPackage ep2) {
            return ep2.getVersion().compareTo(ep1.getVersion());
        }
    }
}